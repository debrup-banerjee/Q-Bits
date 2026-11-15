package com.qbits.resources;

import com.qbits.resources.domain.Candidate;
import com.qbits.resources.domain.ResourceHost;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Asks each host's official API whether a project exists (spec 005 R4). Keeps nothing from the
 * response except the status and, for GitHub, the canonical owner/repo (R4.7).
 */
@Component
public class HostApis implements LinkChecker {

  private static final Logger log = LoggerFactory.getLogger(HostApis.class);

  private final RestClient http;
  private final ResourcesProperties props;
  private final JsonMapper json;
  private final HostLimiter limiter;

  public HostApis(
      @Qualifier("linkCheckRestClient") RestClient http,
      ResourcesProperties props,
      JsonMapper json,
      Clock clock) {
    this.http = http;
    this.props = props;
    this.json = json;
    this.limiter = new HostLimiter(props.rateLimits(), clock);
  }

  @Override
  public CheckResult check(Candidate c) {
    if (!limiter.tryAcquire(c.host())) {
      return new CheckResult.Throttled();
    }
    try {
      return switch (c.host()) {
        case GITHUB -> github(c.name());
        case GITLAB -> gitlab(c.name());
        case HUGGINGFACE -> huggingFace(c);
        case ARXIV -> arxiv(c.name());
      };
    } catch (RuntimeException e) {
      log.debug("link check failed host={} error={}", c.host(), e.getClass().getSimpleName());
      return new CheckResult.Failed("network: " + e.getClass().getSimpleName());
    }
  }

  private CheckResult github(String ownerRepo) {
    URI uri = URI.create(base(ResourceHost.GITHUB) + "/repos/" + ownerRepo);
    return http.get()
        .uri(uri)
        .headers(
            h -> {
              h.set(HttpHeaders.ACCEPT, "application/vnd.github+json");
              h.set("X-GitHub-Api-Version", "2022-11-28");
              if (props.githubToken() != null && !props.githubToken().isBlank()) {
                h.setBearerAuth(props.githubToken());
              }
            })
        .exchange(
            (req, res) -> {
              int status = res.getStatusCode().value();
              if (status == 200) {
                JsonNode body = json.readTree(res.getBody());
                String fullName = body.path("full_name").asString(null);
                return new CheckResult.Found(fullName);
              }
              return byStatus(status, false);
            });
  }

  private CheckResult gitlab(String path) {
    String encoded = URLEncoder.encode(path, StandardCharsets.UTF_8);
    URI uri = URI.create(base(ResourceHost.GITLAB) + "/api/v4/projects/" + encoded);
    return http.get().uri(uri).exchange((req, res) -> byStatus(res.getStatusCode().value(), false));
  }

  private CheckResult huggingFace(Candidate c) {
    String kind =
        switch (c.type()) {
          case DATASET -> "datasets";
          case CODE -> "spaces";
          default -> "models";
        };
    URI uri = URI.create(base(ResourceHost.HUGGINGFACE) + "/api/" + kind + "/" + c.name());
    return http.get().uri(uri).exchange((req, res) -> byStatus(res.getStatusCode().value(), true));
  }

  private CheckResult arxiv(String id) {
    URI uri =
        URI.create(
            base(ResourceHost.ARXIV)
                + "/api/query?id_list="
                + URLEncoder.encode(id, StandardCharsets.UTF_8));
    return http.get()
        .uri(uri)
        .exchange(
            (req, res) -> {
              int status = res.getStatusCode().value();
              if (status != 200) {
                return byStatus(status, false);
              }
              String body = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
              boolean error = body.contains("<title>Error</title>");
              boolean hasEntry = body.contains("<entry>") && body.contains("/abs/" + id);
              return !error && hasEntry ? new CheckResult.Found(null) : new CheckResult.Missing();
            });
  }

  /** 404 (and 401 for Hugging Face gated/private repos) means missing; 2xx found; else failed. */
  private static CheckResult byStatus(int status, boolean unauthorisedIsMissing) {
    if (status >= 200 && status < 300) {
      return new CheckResult.Found(null);
    }
    if (status == 404 || status == 410 || (unauthorisedIsMissing && status == 401)) {
      return new CheckResult.Missing();
    }
    return new CheckResult.Failed("HTTP " + status);
  }

  private String base(ResourceHost host) {
    String base = props.baseUrls().get(host);
    return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
  }
}
