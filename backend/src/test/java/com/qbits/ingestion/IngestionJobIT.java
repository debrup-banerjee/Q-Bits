package com.qbits.ingestion;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.qbits.IntegrationTest;
import com.qbits.MutableClock;
import com.qbits.ingestion.domain.FetchLogEntry;
import com.qbits.ingestion.domain.FetchStatus;
import com.qbits.ingestion.domain.StoryStatus;
import com.qbits.ingestion.persistence.FetchLogRepository;
import com.qbits.ingestion.persistence.ItemRepository;
import com.qbits.ingestion.persistence.RobotsCacheRepository;
import com.qbits.ingestion.persistence.SourceStateRepository;
import com.qbits.relevance.domain.RelevancePreFilter;
import com.qbits.sources.SourceRegistry;
import com.qbits.sources.domain.Region;
import com.qbits.sources.domain.Source;
import com.qbits.sources.domain.SourceType;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestClient;

class IngestionJobIT extends IntegrationTest {

  @RegisterExtension
  static WireMockExtension site =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().gzipDisabled(true))
          .build();

  private static final Instant NOW = Instant.parse("2026-10-03T06:00:00Z");

  @Autowired RestClient feedRestClient;
  @Autowired RobotsCacheRepository robotsCache;
  @Autowired FeedFetcher fetcher;
  @Autowired FeedParser parser;
  @Autowired RelevancePreFilter preFilter;
  @Autowired Deduplicator deduplicator;
  @Autowired ItemRepository items;
  @Autowired SourceStateRepository states;
  @Autowired FetchLogRepository fetchLog;
  @Autowired SourceHealthTracker health;
  @Autowired IngestionProperties props;
  @Autowired LockingTaskExecutor locks;

  private MutableClock clock;

  @BeforeEach
  void setUp() {
    clock = new MutableClock(NOW);
    site.resetAll();
    site.stubFor(get(urlEqualTo("/robots.txt")).willReturn(aResponse().withStatus(404)));
    stubFeed("/news/rss.xml", "rss-sample.xml");
    stubFeed("/lab/atom.xml", "atom-sample.xml");
    site.stubFor(get(urlEqualTo("/broken/feed")).willReturn(aResponse().withStatus(500)));
  }

  @Test
  void ingestsFixtureFeedsEndToEnd() { // 001 R2.1, R4, R5, R7.2
    job(sources()).runOnce();

    List<FetchLogEntry> news = fetchLog.findBySource("tech-news");
    assertThat(news)
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.status()).isEqualTo(FetchStatus.OK);
              assertThat(e.itemsNew()).isEqualTo(1);
              assertThat(e.itemsNotAi()).isEqualTo(1); // football story
              assertThat(e.itemsMalformed()).isEqualTo(1); // entry with no title
            });
    assertThat(fetchLog.findBySource("ai-lab").getFirst().itemsNew()).isEqualTo(1);

    assertThat(items.existsByCanonicalUrl("https://news.example.com/ai-model")).isTrue();
    Long pending =
        jdbc.queryForObject(
            "select count(*) from items where story_status = ?",
            Long.class,
            StoryStatus.PENDING.name());
    assertThat(pending).isEqualTo(2);
  }

  @Test
  void rerunningSameContentCreatesNothing() { // 001 R6.3
    IngestionJob job = job(sources());
    job.runOnce();
    clock.advance(Duration.ofMinutes(31));

    job.runOnce();

    assertThat(items.countBySource("tech-news")).isEqualTo(1);
    assertThat(fetchLog.findBySource("tech-news").getFirst().itemsDuplicate()).isEqualTo(1);
  }

  @Test
  void doesNotFetchBeforeIntervalHasPassed() { // 001 R2.1
    IngestionJob job = job(sources());
    job.runOnce();
    clock.advance(Duration.ofMinutes(10));

    job.runOnce();

    site.verify(1, getRequestedFor(urlEqualTo("/news/rss.xml")));
  }

  @Test
  void failingSourceDoesNotAffectOthers() { // 001 R7.3
    job(sources()).runOnce();

    assertThat(fetchLog.findBySource("broken").getFirst().status()).isEqualTo(FetchStatus.FAILED);
    assertThat(items.countBySource("ai-lab")).isEqualTo(1);
    assertThat(states.find("broken").orElseThrow().consecutiveFailures()).isEqualTo(1);
  }

  @Test
  void usesStoredValidatorsAndRecordsNotModified() { // 001 R2.2, R2.3
    site.stubFor(
        get(urlEqualTo("/lab/atom.xml"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("ETag", "\"lab-1\"")
                    .withBody(FeedParserTest.fixture("atom-sample.xml"))));
    IngestionJob job = job(sources());
    job.runOnce();
    site.stubFor(
        get(urlEqualTo("/lab/atom.xml"))
            .withHeader(
                "If-None-Match",
                com.github.tomakehurst.wiremock.client.WireMock.equalTo("\"lab-1\""))
            .willReturn(aResponse().withStatus(304)));
    clock.advance(Duration.ofMinutes(31));

    job.runOnce();

    assertThat(fetchLog.findBySource("ai-lab").getFirst().status())
        .isEqualTo(FetchStatus.NOT_MODIFIED);
  }

  @Test
  void treatsSameTitleFromSameSourceAsDuplicate() { // 001 R6.2
    IngestionJob job = job(sources());
    job.runOnce();
    String moved =
        new String(FeedParserTest.fixture("atom-sample.xml"))
            .replace(
                "https://lab.example.org/robots-laundry",
                "https://lab.example.org/robots-laundry-v2");
    site.stubFor(
        get(urlEqualTo("/lab/atom.xml")).willReturn(aResponse().withStatus(200).withBody(moved)));
    clock.advance(Duration.ofMinutes(31));

    job.runOnce();

    assertThat(items.countBySource("ai-lab")).isEqualTo(1);
  }

  @Test
  void skipsSourceBlockedByRobots() { // 001 R3.2
    site.stubFor(
        get(urlEqualTo("/robots.txt"))
            .willReturn(aResponse().withStatus(200).withBody("User-agent: *\nDisallow: /news/\n")));

    job(sources()).runOnce();

    assertThat(fetchLog.findBySource("tech-news").getFirst().status())
        .isEqualTo(FetchStatus.BLOCKED_BY_ROBOTS);
    site.verify(0, getRequestedFor(urlEqualTo("/news/rss.xml")));
  }

  @Test
  void usesCrawlDelayWhenLongerThanInterval() { // 001 R3.3
    site.stubFor(
        get(urlEqualTo("/robots.txt"))
            .willReturn(
                aResponse().withStatus(200).withBody("User-agent: *\nCrawl-delay: 3600\n")));
    IngestionJob job = job(sources());
    job.runOnce();
    clock.advance(Duration.ofMinutes(31));

    job.runOnce();

    site.verify(1, getRequestedFor(urlEqualTo("/news/rss.xml")));
  }

  @Test
  void twoInstancesFetchEachSourceOnce() throws Exception { // 001 R2.5
    IngestionJob first = job(sources());
    IngestionJob second = job(sources());

    CompletableFuture<Boolean> a = CompletableFuture.supplyAsync(first::runOnce);
    CompletableFuture<Boolean> b = CompletableFuture.supplyAsync(second::runOnce);
    a.get();
    b.get();

    site.verify(1, getRequestedFor(urlEqualTo("/news/rss.xml")));
    site.verify(1, getRequestedFor(urlEqualTo("/lab/atom.xml")));
  }

  private IngestionJob job(List<Source> sources) {
    RobotsPolicy robots = new RobotsPolicy(feedRestClient, robotsCache, props, clock);
    SourceIngestor ingestor =
        new SourceIngestor(
            robots,
            fetcher,
            parser,
            preFilter,
            deduplicator,
            items,
            states,
            fetchLog,
            health,
            props,
            clock);
    return new IngestionJob(new SourceRegistry(sources), ingestor, locks);
  }

  private List<Source> sources() {
    return List.of(
        source("tech-news", "/news/rss.xml", SourceType.RSS, false),
        source("ai-lab", "/lab/atom.xml", SourceType.ATOM, true),
        source("broken", "/broken/feed", SourceType.RSS, true));
  }

  private static Source source(String id, String path, SourceType type, boolean aiNative) {
    return new Source(
        id,
        id,
        URI.create(site.baseUrl()),
        URI.create(site.baseUrl() + path),
        type,
        URI.create(site.baseUrl() + "/terms"),
        LocalDate.of(2026, 10, 3),
        true,
        aiNative,
        Region.GLOBAL,
        null,
        null);
  }

  private static void stubFeed(String path, String fixture) {
    site.stubFor(
        get(urlEqualTo(path))
            .willReturn(aResponse().withStatus(200).withBody(FeedParserTest.fixture(fixture))));
  }
}
