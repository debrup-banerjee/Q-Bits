package com.qbits.ingestion;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
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
import com.qbits.stories.FakeStoryWriter;
import com.qbits.stories.StoryFixtures;
import com.qbits.stories.StoryJob;
import com.qbits.stories.StoryService;
import com.qbits.stories.StoryWriter;
import com.qbits.stories.StoryWriterProperties;
import com.qbits.stories.persistence.JobBackoffRepository;
import com.qbits.stories.persistence.StoryRepository;
import com.qbits.stories.persistence.WriterBudgetRepository;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

class IngestionJobIT extends IntegrationTest {

  @RegisterExtension
  static WireMockExtension site =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().gzipDisabled(true))
          .build();

  /** A host that is not in the sources file. Nothing may ever reach it. */
  @RegisterExtension
  static WireMockExtension elsewhere =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

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
  @Autowired com.qbits.resources.ResourceCollector resourceCollector;
  @Autowired com.qbits.images.FeedImageCollector feedImages;
  @Autowired com.qbits.resources.persistence.ItemResourceRepository itemResources;
  @Autowired StoryRepository stories;
  @Autowired WriterBudgetRepository budget;
  @Autowired JobBackoffRepository backoff;
  @Autowired TransactionTemplate tx;

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
  void requestsOnlyRegisteredFeedUrlsAndTheirRobotsTxtEvenOnRedirects() { // 001 R1.3
    elsewhere.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(200).withBody("<rss/>")));
    site.stubFor(
        get(urlEqualTo("/lab/atom.xml"))
            .willReturn(
                aResponse()
                    .withStatus(301)
                    .withHeader("Location", elsewhere.baseUrl() + "/lab/atom.xml")));
    site.stubFor(
        get(urlEqualTo("/broken/feed"))
            .willReturn(
                aResponse().withStatus(302).withHeader("Location", site.baseUrl() + "/moved")));
    site.stubFor(get(urlEqualTo("/moved")).willReturn(aResponse().withStatus(200)));

    job(sources()).runOnce();

    assertThat(site.getAllServeEvents())
        .extracting(e -> e.getRequest().getUrl())
        .isNotEmpty()
        .allMatch(
            url ->
                Set.of("/robots.txt", "/news/rss.xml", "/lab/atom.xml", "/broken/feed")
                    .contains(url));
    assertThat(elsewhere.getAllServeEvents()).isEmpty();
    FetchLogEntry crossHost = fetchLog.findBySource("ai-lab").getFirst();
    assertThat(crossHost.status()).isEqualTo(FetchStatus.FAILED);
    assertThat(crossHost.httpStatus()).isEqualTo(301);
    assertThat(crossHost.error()).contains(elsewhere.baseUrl() + "/lab/atom.xml");
    FetchLogEntry sameHost = fetchLog.findBySource("broken").getFirst();
    assertThat(sameHost.status()).isEqualTo(FetchStatus.FAILED);
    assertThat(sameHost.httpStatus()).isEqualTo(302);
    assertThat(fetchLog.findBySource("tech-news").getFirst().status()).isEqualTo(FetchStatus.OK);
  }

  @Test
  void waitsUntilRetryAfterThenClearsItAfterASuccessfulFetch() { // 001 R7.1
    site.stubFor(
        get(urlEqualTo("/lab/atom.xml"))
            .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "7200")));
    IngestionJob job = job(sources());

    job.runOnce();

    assertThat(states.find("ai-lab").orElseThrow().retryAfter())
        .isEqualTo(NOW.plus(Duration.ofHours(2)));
    assertThat(fetchLog.findBySource("ai-lab").getFirst().httpStatus()).isEqualTo(429);

    clock.advance(Duration.ofMinutes(31)); // interval passed, Retry-After not yet
    job.runOnce();
    site.verify(1, getRequestedFor(urlEqualTo("/lab/atom.xml")));
    site.verify(2, getRequestedFor(urlEqualTo("/news/rss.xml")));

    stubFeed("/lab/atom.xml", "atom-sample.xml");
    clock.advance(Duration.ofMinutes(90)); // now past Retry-After
    job.runOnce();

    site.verify(2, getRequestedFor(urlEqualTo("/lab/atom.xml")));
    assertThat(fetchLog.findBySource("ai-lab").getFirst().status()).isEqualTo(FetchStatus.OK);
    assertThat(states.find("ai-lab").orElseThrow().retryAfter()).isNull();
  }

  @Test
  void honoursRetryAfterDateOnServiceUnavailableCappedAtOneDay() { // 001 R7.1
    site.stubFor(
        get(urlEqualTo("/lab/atom.xml"))
            .willReturn(
                aResponse()
                    .withStatus(503)
                    .withHeader("Retry-After", "Sat, 03 Oct 2026 09:00:00 GMT")));
    site.stubFor(
        get(urlEqualTo("/news/rss.xml"))
            .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "604800")));

    job(sources()).runOnce();

    site.verify(1, getRequestedFor(urlEqualTo("/lab/atom.xml"))); // not retried at once
    assertThat(states.find("ai-lab").orElseThrow().retryAfter())
        .isEqualTo(Instant.parse("2026-10-03T09:00:00Z"));
    assertThat(states.find("tech-news").orElseThrow().retryAfter())
        .isEqualTo(NOW.plus(Duration.ofHours(24)));
  }

  @Test
  void robotsTxtThatKeepsRedirectingDegradesTheSource() { // 001 R7.4, R3.4, R1.3
    site.stubFor(
        get(urlEqualTo("/robots.txt"))
            .willReturn(
                aResponse()
                    .withStatus(301)
                    .withHeader("Location", elsewhere.baseUrl() + "/robots.txt")));
    IngestionJob job = job(sources());

    for (int run = 1; run <= 5; run++) {
      job.runOnce();
      clock.advance(Duration.ofMinutes(31));
    }

    var state = states.find("tech-news").orElseThrow();
    assertThat(state.consecutiveFailures()).isEqualTo(5);
    assertThat(state.health()).isEqualTo(com.qbits.ingestion.domain.SourceHealth.DEGRADED);
    assertThat(fetchLog.findBySource("tech-news"))
        .allMatch(e -> e.status() == FetchStatus.ROBOTS_UNAVAILABLE);
    assertThat(elsewhere.getAllServeEvents()).isEmpty();
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
  void storesOpenSourceCandidatesAndNeverTheContentText() { // 005 R1.3, R1.2, R2, R3
    stubFeed("/links/rss.xml", "rss-links.xml");

    job(List.of(source("links-lab", "/links/rss.xml", SourceType.RSS, true))).runOnce();

    java.util.UUID item =
        jdbc.queryForObject(
            "select id from items where source_id = 'links-lab'", java.util.UUID.class);
    assertThat(itemResources.findByItem(item))
        .extracting(r -> r.type() + " " + r.url() + " " + r.origin() + " " + r.status())
        .containsExactly(
            "MODEL https://huggingface.co/kestrel-ai/Kestrel-70B DESCRIPTION PENDING",
            "PAPER https://arxiv.org/abs/2410.01234 DESCRIPTION PENDING",
            "CODE https://github.com/kestrel-ai/kestrel CONTENT PENDING");
    Long leaks =
        jdbc.queryForObject(
            """
            select count(*) from (
              select row_to_json(t)::text j from items t
              union all select row_to_json(t)::text from item_resources t
              union all select row_to_json(t)::text from source_fetch_log t) x
            where j like '%FULL ARTICLE%'
            """,
            Long.class);
    assertThat(leaks).isZero();
  }

  @Test
  void keepsIngestingWhileTheSummaryServiceIsDown() { // 002 R9.2
    IngestionJob ingestion = job(sources());
    FakeStoryWriter down =
        new FakeStoryWriter(
            in -> {
              throw new StoryWriter.WriterUnavailable("service down", null);
            });
    StoryJob storyJob = storyJob(down, sources());

    ingestion.runOnce();
    storyJob.runOnce();
    assertThat(down.inputs).isNotEmpty();
    assertThat(backoff.find("stories")).isPresent(); // the story job is backing off

    String next =
        new String(FeedParserTest.fixture("atom-sample.xml"))
            .replace("urn:example:1", "urn:example:2")
            .replace("https://lab.example.org/robots-laundry", "https://lab.example.org/chip-news")
            .replace(
                "Robots learn to fold laundry with machine learning",
                "New AI chip speeds up neural network training");
    site.stubFor(
        get(urlEqualTo("/lab/atom.xml")).willReturn(aResponse().withStatus(200).withBody(next)));
    clock.advance(Duration.ofMinutes(31));

    ingestion.runOnce();
    storyJob.runOnce();

    assertThat(items.existsByCanonicalUrl("https://lab.example.org/chip-news")).isTrue();
    assertThat(items.countBySource("ai-lab")).isEqualTo(2);
    assertThat(fetchLog.findBySource("ai-lab"))
        .hasSize(2)
        .allSatisfy(e -> assertThat(e.status()).isEqualTo(FetchStatus.OK));
    Long pending =
        jdbc.queryForObject(
            "select count(*) from items where story_status = ?",
            Long.class,
            StoryStatus.PENDING.name());
    assertThat(pending).isEqualTo(3);
    assertThat(jdbc.queryForObject("select count(*) from stories", Long.class)).isZero();
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
            resourceCollector,
            feedImages,
            clock);
    SourceRegistry registry = new SourceRegistry(sources);
    return new IngestionJob(
        registry, ingestor, locks, new SourceVisibility(registry, items), clock);
  }

  private StoryJob storyJob(StoryWriter writer, List<Source> sources) {
    StoryWriterProperties storyProps = StoryFixtures.props();
    StoryService service =
        new StoryService(writer, stories, new ItemStore(items), storyProps, tx, clock);
    return new StoryJob(
        new ItemStore(items),
        new SourceRegistry(sources),
        service,
        budget,
        backoff,
        storyProps,
        locks,
        clock);
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
        null,
        false);
  }

  private static void stubFeed(String path, String fixture) {
    site.stubFor(
        get(urlEqualTo(path))
            .willReturn(aResponse().withStatus(200).withBody(FeedParserTest.fixture(fixture))));
  }
}
