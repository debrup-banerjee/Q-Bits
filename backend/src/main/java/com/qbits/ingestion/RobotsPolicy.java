package com.qbits.ingestion;

import com.qbits.ingestion.persistence.RobotsCacheRepository;
import com.qbits.ingestion.persistence.RobotsCacheRepository.CachedRobots;
import crawlercommons.robots.BaseRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Checks robots.txt before any feed fetch (spec 001 R3). Responses are cached in the database for
 * the configured TTL so all instances share them; failures are not cached.
 */
@Service
public class RobotsPolicy {

  private static final Logger log = LoggerFactory.getLogger(RobotsPolicy.class);

  private final RestClient http;
  private final RobotsCacheRepository cache;
  private final IngestionProperties props;
  private final Clock clock;
  private final SimpleRobotRulesParser parser = new SimpleRobotRulesParser();

  public RobotsPolicy(
      @Qualifier("feedRestClient") RestClient feedRestClient,
      RobotsCacheRepository cache,
      IngestionProperties props,
      Clock clock) {
    this.http = feedRestClient;
    this.cache = cache;
    this.props = props;
    this.clock = clock;
    // Honour long Crawl-delays as a slower interval (R3.3) instead of treating them as "disallow".
    this.parser.setMaxCrawlDelay(Long.MAX_VALUE);
  }

  public RobotsDecision check(URI feedUrl) {
    String authority = feedUrl.getRawAuthority().toLowerCase(Locale.ROOT);
    Instant now = clock.instant();

    Optional<CachedRobots> cached =
        cache.find(authority).filter(c -> c.fetchedAt().plus(props.robotsCacheTtl()).isAfter(now));
    CachedRobots robots;
    if (cached.isPresent()) {
      robots = cached.get();
    } else {
      Optional<CachedRobots> fetched = fetch(feedUrl, authority, now);
      if (fetched.isEmpty()) {
        return new RobotsDecision.Unavailable("robots.txt unavailable for " + authority);
      }
      robots = fetched.get();
      cache.save(robots);
    }
    return decide(feedUrl, robots);
  }

  private Optional<CachedRobots> fetch(URI feedUrl, String authority, Instant now) {
    URI robotsUrl =
        URI.create(feedUrl.getScheme() + "://" + feedUrl.getRawAuthority() + "/robots.txt");
    try {
      return http.get()
          .uri(robotsUrl)
          .exchange(
              (request, response) -> {
                int status = response.getStatusCode().value();
                if (status >= 500 || status == 429) {
                  // Server error or rate limited: skip this cycle, cache nothing (R3.4).
                  log.warn("robots unavailable host={} status={}", authority, status);
                  return Optional.<CachedRobots>empty();
                }
                if (status >= 300 && status < 400) {
                  // Redirects are not followed (R1.3): treat as unavailable, cache nothing.
                  log.warn(
                      "robots unavailable host={} status={} redirectTarget={}",
                      authority,
                      status,
                      response.getHeaders().getFirst(HttpHeaders.LOCATION));
                  return Optional.<CachedRobots>empty();
                }
                String body =
                    status == 200
                        ? new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)
                        : null;
                return Optional.of(new CachedRobots(authority, body, status, now));
              });
    } catch (RuntimeException e) {
      log.warn("robots unavailable host={} error={}", authority, e.getClass().getSimpleName());
      return Optional.empty();
    }
  }

  private RobotsDecision decide(URI feedUrl, CachedRobots robots) {
    if (robots.statusCode() == 401 || robots.statusCode() == 403) {
      // The host refuses us its robots.txt: never fetch past a login or bot check.
      return new RobotsDecision.Disallowed();
    }
    if (robots.statusCode() != 200 || robots.body() == null) {
      // 404 and other client errors: no robots.txt, so fetching is allowed (R3.4, RFC 9309).
      return new RobotsDecision.Allowed(Optional.empty());
    }
    BaseRobotRules rules =
        parser.parseContent(
            feedUrl.toString(),
            robots.body().getBytes(StandardCharsets.UTF_8),
            "text/plain",
            List.of(props.robotsAgentName().toLowerCase(Locale.ROOT)));
    if (!rules.isAllowed(feedUrl.toString())) {
      return new RobotsDecision.Disallowed();
    }
    long delayMs = rules.getCrawlDelay();
    Optional<Duration> delay =
        delayMs > 0 ? Optional.of(Duration.ofMillis(delayMs)) : Optional.empty();
    return new RobotsDecision.Allowed(delay);
  }
}
