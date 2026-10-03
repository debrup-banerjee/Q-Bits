package com.qbits.resources;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.qbits.IntegrationTest;
import com.qbits.MutableClock;
import com.qbits.resources.ResourcesProperties.RateLimit;
import com.qbits.resources.domain.Candidate;
import com.qbits.resources.domain.ResourceHost;
import com.qbits.resources.domain.ResourceLinkNormaliser;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class HostApisIT extends IntegrationTest {

  @RegisterExtension
  static WireMockExtension api =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().gzipDisabled(true))
          .build();

  @Autowired RestClient feedRestClient;
  @Autowired JsonMapper json;

  private final ResourceLinkNormaliser normaliser = new ResourceLinkNormaliser();
  private MutableClock clock;

  @BeforeEach
  void setUp() {
    clock = new MutableClock(Instant.parse("2026-10-03T06:00:00Z"));
  }

  private HostApis apis(String token, int perHour, Duration spacing) {
    String base = api.baseUrl();
    RateLimit limit = new RateLimit(perHour, spacing);
    ResourcesProperties props =
        new ResourcesProperties(
            Duration.ofMinutes(5),
            30,
            3,
            Duration.ofHours(24),
            List.of(Duration.ofMinutes(10), Duration.ofHours(1), Duration.ofHours(6)),
            token,
            Map.of(
                ResourceHost.GITHUB,
                base,
                ResourceHost.GITLAB,
                base,
                ResourceHost.HUGGINGFACE,
                base,
                ResourceHost.ARXIV,
                base),
            Map.of(
                ResourceHost.GITHUB,
                limit,
                ResourceHost.GITLAB,
                limit,
                ResourceHost.HUGGINGFACE,
                limit,
                ResourceHost.ARXIV,
                limit));
    return new HostApis(feedRestClient, props, json, clock);
  }

  private Candidate c(String url) {
    return normaliser.normalise(url).orElseThrow();
  }

  @Test
  void githubFoundMissingAndRenamed() { // 005 R4.1–R4.3
    api.stubFor(
        get(urlEqualTo("/repos/openai/whisper"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"full_name\":\"openai/whisper\",\"description\":\"ignored\"}")));
    api.stubFor(
        get(urlEqualTo("/repos/old/name"))
            .willReturn(
                aResponse()
                    .withStatus(301)
                    .withHeader("Location", api.baseUrl() + "/repositories/42")));
    api.stubFor(
        get(urlEqualTo("/repositories/42"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"full_name\":\"new-org/new-name\"}")));
    api.stubFor(get(urlEqualTo("/repos/no/such")).willReturn(aResponse().withStatus(404)));
    HostApis apis = apis(null, 100, Duration.ZERO);

    assertThat(apis.check(c("https://github.com/openai/whisper")))
        .isEqualTo(new CheckResult.Found("openai/whisper"));
    assertThat(apis.check(c("https://github.com/old/name")))
        .isEqualTo(new CheckResult.Found("new-org/new-name"));
    assertThat(apis.check(c("https://github.com/no/such"))).isEqualTo(new CheckResult.Missing());
  }

  @Test
  void githubRateLimitAndTimeoutAreFailures() { // 005 R4.4
    api.stubFor(get(urlEqualTo("/repos/a/limited")).willReturn(aResponse().withStatus(403)));
    api.stubFor(get(urlEqualTo("/repos/a/busy")).willReturn(aResponse().withStatus(429)));
    api.stubFor(
        get(urlEqualTo("/repos/a/slow"))
            .willReturn(aResponse().withStatus(200).withFixedDelay(5_000)));
    HostApis apis = apis(null, 100, Duration.ZERO);

    assertThat(apis.check(c("https://github.com/a/limited")))
        .isInstanceOf(CheckResult.Failed.class);
    assertThat(apis.check(c("https://github.com/a/busy"))).isInstanceOf(CheckResult.Failed.class);
    assertThat(apis.check(c("https://github.com/a/slow"))).isInstanceOf(CheckResult.Failed.class);
  }

  @Test
  void sendsTokenOnlyWhenConfiguredAndAlwaysTheUserAgent() { // 005 R4.6
    api.stubFor(
        get(urlPathEqualTo("/repos/a/b")).willReturn(aResponse().withStatus(200).withBody("{}")));

    apis(null, 100, Duration.ZERO).check(c("https://github.com/a/b"));
    api.verify(
        getRequestedFor(urlEqualTo("/repos/a/b"))
            .withHeader("Authorization", absent())
            .withHeader("User-Agent", equalTo("QBits/0.1 (+mailto:debrup28.nitdgp@gmail.com)")));

    api.resetRequests();
    apis("tok123", 100, Duration.ZERO).check(c("https://github.com/a/b"));
    api.verify(
        getRequestedFor(urlEqualTo("/repos/a/b"))
            .withHeader("Authorization", equalTo("Bearer tok123")));
  }

  @Test
  void gitlabAndHuggingFace() { // 005 R4.1–R4.3
    api.stubFor(
        get(urlEqualTo("/api/v4/projects/group%2Fsub%2Fproject"))
            .willReturn(aResponse().withStatus(200).withBody("{}")));
    api.stubFor(
        get(urlEqualTo("/api/models/meta-llama/Llama-4-8B"))
            .willReturn(aResponse().withStatus(200).withBody("{}")));
    api.stubFor(
        get(urlEqualTo("/api/datasets/allenai/c4"))
            .willReturn(aResponse().withStatus(200).withBody("{}")));
    api.stubFor(get(urlEqualTo("/api/spaces/org/demo")).willReturn(aResponse().withStatus(404)));
    api.stubFor(get(urlEqualTo("/api/models/gated/model")).willReturn(aResponse().withStatus(401)));
    HostApis apis = apis(null, 100, Duration.ZERO);

    assertThat(apis.check(c("https://gitlab.com/group/sub/project")))
        .isInstanceOf(CheckResult.Found.class);
    assertThat(apis.check(c("https://huggingface.co/meta-llama/Llama-4-8B")))
        .isInstanceOf(CheckResult.Found.class);
    assertThat(apis.check(c("https://huggingface.co/datasets/allenai/c4")))
        .isInstanceOf(CheckResult.Found.class);
    assertThat(apis.check(c("https://huggingface.co/spaces/org/demo")))
        .isEqualTo(new CheckResult.Missing());
    assertThat(apis.check(c("https://huggingface.co/gated/model")))
        .isEqualTo(new CheckResult.Missing());
  }

  @Test
  void arxivFoundAndMissing() { // 005 R4.1–R4.3
    api.stubFor(
        get(urlEqualTo("/api/query?id_list=2410.01234"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(
                        "<feed><entry><id>http://arxiv.org/abs/2410.01234v2</id><title>A paper</title></entry></feed>")));
    api.stubFor(
        get(urlEqualTo("/api/query?id_list=2410.99999"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(
                        "<feed><entry><id>http://arxiv.org/api/errors</id><title>Error</title></entry></feed>")));
    HostApis apis = apis(null, 100, Duration.ZERO);

    assertThat(apis.check(c("https://arxiv.org/abs/2410.01234")))
        .isInstanceOf(CheckResult.Found.class);
    assertThat(apis.check(c("https://arxiv.org/abs/2410.99999")))
        .isEqualTo(new CheckResult.Missing());
  }

  @Test
  void ownLimiterThrottlesWithoutCallingTheHost() { // 005 R4.6
    api.stubFor(
        get(urlPathEqualTo("/api/query"))
            .willReturn(aResponse().withStatus(200).withBody("<feed/>")));
    HostApis spaced = apis(null, 100, Duration.ofSeconds(3));

    spaced.check(c("https://arxiv.org/abs/2410.00001"));
    assertThat(spaced.check(c("https://arxiv.org/abs/2410.00002")))
        .isEqualTo(new CheckResult.Throttled());
    clock.advance(Duration.ofSeconds(3));
    assertThat(spaced.check(c("https://arxiv.org/abs/2410.00003")))
        .isNotEqualTo(new CheckResult.Throttled());
    api.verify(2, getRequestedFor(urlPathEqualTo("/api/query")));

    HostApis hourly = apis(null, 2, Duration.ZERO);
    hourly.check(c("https://arxiv.org/abs/2410.00004"));
    hourly.check(c("https://arxiv.org/abs/2410.00005"));
    assertThat(hourly.check(c("https://arxiv.org/abs/2410.00006")))
        .isEqualTo(new CheckResult.Throttled());
    clock.advance(Duration.ofMinutes(61));
    assertThat(hourly.check(c("https://arxiv.org/abs/2410.00007")))
        .isNotEqualTo(new CheckResult.Throttled());
  }
}
