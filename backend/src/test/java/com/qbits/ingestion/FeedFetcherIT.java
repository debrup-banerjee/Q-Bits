package com.qbits.ingestion;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.qbits.IntegrationTest;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;

class FeedFetcherIT extends IntegrationTest {

  @RegisterExtension
  static WireMockExtension site =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().gzipDisabled(true))
          .build();

  @Autowired FeedFetcher fetcher;
  @Autowired IngestionProperties props;

  private URI feed;

  @BeforeEach
  void setUp() {
    feed = URI.create(site.baseUrl() + "/feed.xml");
  }

  @Test
  void returnsBodyAndValidators() { // 001 R2.2
    site.stubFor(
        get(urlEqualTo("/feed.xml"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("ETag", "\"v1\"")
                    .withHeader("Last-Modified", "Sat, 03 Oct 2026 04:00:00 GMT")
                    .withBody("<rss/>")));

    FetchOutcome outcome = fetcher.fetch(feed, null, null);

    assertThat(outcome)
        .isInstanceOfSatisfying(
            FetchOutcome.Fetched.class,
            f -> {
              assertThat(new String(f.body())).isEqualTo("<rss/>");
              assertThat(f.etag()).isEqualTo("\"v1\"");
              assertThat(f.lastModified()).isEqualTo("Sat, 03 Oct 2026 04:00:00 GMT");
            });
  }

  @Test
  void sendsConditionalHeadersAndHandlesNotModified() { // 001 R2.2, R2.3
    site.stubFor(
        get(urlEqualTo("/feed.xml"))
            .withHeader("If-None-Match", equalTo("\"v1\""))
            .withHeader("If-Modified-Since", equalTo("Sat, 03 Oct 2026 04:00:00 GMT"))
            .willReturn(aResponse().withStatus(304)));

    FetchOutcome outcome = fetcher.fetch(feed, "\"v1\"", "Sat, 03 Oct 2026 04:00:00 GMT");

    assertThat(outcome).isInstanceOf(FetchOutcome.NotModified.class);
  }

  @Test
  void sendsHonestUserAgent() { // 001 R2.4
    site.stubFor(
        get(urlEqualTo("/feed.xml")).willReturn(aResponse().withStatus(200).withBody("x")));

    fetcher.fetch(feed, null, null);

    site.verify(
        getRequestedFor(urlEqualTo("/feed.xml"))
            .withHeader("User-Agent", equalTo("QBits/0.1 (+mailto:debrup28.nitdgp@gmail.com)")));
  }

  @Test
  void retriesAfterTimeoutThenSucceeds() { // 001 R7.1
    site.stubFor(
        get(urlEqualTo("/feed.xml"))
            .inScenario("slow")
            .whenScenarioStateIs(STARTED)
            .willReturn(aResponse().withStatus(200).withFixedDelay(5_000))
            .willSetStateTo("fast"));
    site.stubFor(
        get(urlEqualTo("/feed.xml"))
            .inScenario("slow")
            .whenScenarioStateIs("fast")
            .willReturn(aResponse().withStatus(200).withBody("ok")));

    assertThat(fetcher.fetch(feed, null, null)).isInstanceOf(FetchOutcome.Fetched.class);
  }

  @Test
  void givesUpAfterTwoRetriesOnServerErrors() { // 001 R7.1
    site.stubFor(get(urlEqualTo("/feed.xml")).willReturn(aResponse().withStatus(500)));

    FetchOutcome outcome = fetcher.fetch(feed, null, null);

    assertThat(outcome).isEqualTo(new FetchOutcome.Failed(500, "HTTP 500"));
    site.verify(1 + props.retries(), getRequestedFor(urlEqualTo("/feed.xml")));
  }

  @Test
  void doesNotRetryClientErrors() { // 001 R7.1
    site.stubFor(get(urlEqualTo("/feed.xml")).willReturn(aResponse().withStatus(404)));

    assertThat(fetcher.fetch(feed, null, null)).isEqualTo(new FetchOutcome.Failed(404, "HTTP 404"));
    site.verify(1, getRequestedFor(urlEqualTo("/feed.xml")));
  }
}
