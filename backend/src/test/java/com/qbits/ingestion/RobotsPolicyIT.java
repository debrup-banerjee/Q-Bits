package com.qbits.ingestion;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.qbits.IntegrationTest;
import com.qbits.ingestion.persistence.RobotsCacheRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestClient;

class RobotsPolicyIT extends IntegrationTest {

  @RegisterExtension
  static WireMockExtension site =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private static final Instant NOW = Instant.parse("2026-10-03T06:00:00Z");

  @Autowired RestClient feedRestClient;
  @Autowired RobotsCacheRepository cache;
  @Autowired IngestionProperties props;

  private RobotsPolicy policy;
  private URI feed;

  @BeforeEach
  void setUp() {
    policy = new RobotsPolicy(feedRestClient, cache, props, Clock.fixed(NOW, ZoneOffset.UTC));
    feed = URI.create(site.baseUrl() + "/feeds/ai.xml");
  }

  @Test
  void allowsWhenRobotsPermits() { // 001 R3.1
    robots(200, "User-agent: *\nDisallow: /private/\n");

    assertThat(policy.check(feed)).isEqualTo(new RobotsDecision.Allowed(Optional.empty()));
  }

  @Test
  void disallowsWhenFeedPathIsBlocked() { // 001 R3.2
    robots(200, "User-agent: *\nDisallow: /feeds/\n");

    assertThat(policy.check(feed)).isInstanceOf(RobotsDecision.Disallowed.class);
  }

  @Test
  void honoursRulesForOurAgentName() { // 001 R3.2
    robots(200, "User-agent: QBits\nDisallow: /\n\nUser-agent: *\nAllow: /\n");

    assertThat(policy.check(feed)).isInstanceOf(RobotsDecision.Disallowed.class);
  }

  @Test
  void returnsCrawlDelay() { // 001 R3.3
    robots(200, "User-agent: *\nCrawl-delay: 3600\n");

    assertThat(policy.check(feed))
        .isEqualTo(new RobotsDecision.Allowed(Optional.of(Duration.ofHours(1))));
  }

  @Test
  void treatsMissingRobotsAsAllowed() { // 001 R3.4
    robots(404, "Not found");

    assertThat(policy.check(feed)).isInstanceOf(RobotsDecision.Allowed.class);
  }

  @Test
  void skipsWhenRobotsServerErrors() { // 001 R3.4
    robots(503, "busy");

    assertThat(policy.check(feed)).isInstanceOf(RobotsDecision.Unavailable.class);
    assertThat(cache.find(feed.getRawAuthority())).isEmpty();
  }

  @Test
  void skipsWhenRobotsTimesOut() { // 001 R3.4
    site.stubFor(
        get(urlEqualTo("/robots.txt"))
            .willReturn(aResponse().withStatus(200).withFixedDelay(15_000)));

    assertThat(policy.check(feed)).isInstanceOf(RobotsDecision.Unavailable.class);
  }

  @Test
  void usesCacheFor24Hours() { // 001 R3.1
    robots(200, "User-agent: *\nAllow: /\n");
    policy.check(feed);
    policy.check(feed);
    assertThat(
            site.countRequestsMatching(getRequestedFor(urlEqualTo("/robots.txt")).build())
                .getCount())
        .isEqualTo(1);

    RobotsPolicy dayLater =
        new RobotsPolicy(
            feedRestClient,
            cache,
            props,
            Clock.fixed(NOW.plus(Duration.ofHours(25)), ZoneOffset.UTC));
    dayLater.check(feed);
    assertThat(
            site.countRequestsMatching(getRequestedFor(urlEqualTo("/robots.txt")).build())
                .getCount())
        .isEqualTo(2);
  }

  private static void robots(int status, String body) {
    site.stubFor(
        get(urlEqualTo("/robots.txt")).willReturn(aResponse().withStatus(status).withBody(body)));
  }
}
