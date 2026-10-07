package com.qbits.images;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.qbits.IntegrationTest;
import com.qbits.MutableClock;
import com.qbits.catalog.CatalogFixtures;
import com.qbits.images.domain.StoryImage;
import com.qbits.images.persistence.FeedImageRepository;
import com.qbits.images.persistence.StoryImageRepository;
import com.qbits.ingestion.persistence.ItemRepository;
import com.qbits.sources.SourceRegistry;
import com.qbits.sources.domain.ImagePermission;
import com.qbits.sources.domain.Region;
import com.qbits.sources.domain.Source;
import com.qbits.sources.domain.SourceType;
import com.qbits.stories.domain.Section;
import com.qbits.stories.persistence.StoryRepository;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class ImageJobIT extends IntegrationTest {

  private static final Instant NOW = Instant.parse("2026-10-03T06:00:00Z");

  @RegisterExtension
  static WireMockExtension api =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().gzipDisabled(true))
          .build();

  @Autowired ItemRepository items;
  @Autowired StoryRepository stories;
  @Autowired StoryImageRepository images;
  @Autowired FeedImageRepository feedImages;
  @Autowired LockingTaskExecutor locks;
  @Autowired RestClient imagesRestClient;
  @Autowired JsonMapper json;

  private CatalogFixtures seed;
  private MutableClock clock;
  private PexelsClient pexels;

  @BeforeEach
  void setUp() {
    seed = new CatalogFixtures(items, stories);
    clock = new MutableClock(NOW);
  }

  private ImagesProperties props(String key, int perHour) {
    return new ImagesProperties(
        Duration.ofMinutes(5),
        20,
        Duration.ofDays(7),
        List.of(Duration.ofMinutes(30), Duration.ofHours(3)),
        new ImagesProperties.Pexels(
            key, api.baseUrl(), perHour, Duration.ZERO, Duration.ofSeconds(2), "QBits/test"),
        new ImagesProperties.Query(null, "v1", 100, Duration.ofSeconds(5), 10));
  }

  private ImageJob job(String key, int perHour, List<Source> sources) {
    ImagesProperties props = props(key, perHour);
    pexels = new PexelsClient(imagesRestClient, props, json, clock);
    StaticListableBeanFactory none = new StaticListableBeanFactory();
    return new ImageJob(
        images,
        new SourceRegistry(sources),
        pexels,
        none.getBeanProvider(ImageQueryWriter.class),
        props,
        locks,
        clock);
  }

  private static Source source(String id, ImagePermission images) {
    return new Source(
        id,
        "Lab " + id,
        URI.create("https://" + id + ".example/"),
        URI.create("https://" + id + ".example/feed"),
        SourceType.RSS,
        URI.create("https://" + id + ".example/terms"),
        LocalDate.parse("2026-10-01"),
        true,
        true,
        Region.GLOBAL,
        null,
        null,
        true,
        images);
  }

  private static final String RESULTS =
      """
      {"photos": [
        {"id": 101, "url": "https://www.pexels.com/photo/chip-101/", "avg_color": "#0C2340",
         "alt": "a computer chip on a board", "photographer": "Asha Rao",
         "photographer_url": "https://www.pexels.com/@asha",
         "src": {"landscape": "https://images.pexels.com/photos/101/p.jpeg?fit=crop&h=627&w=1200"}},
        {"id": 102, "url": "https://www.pexels.com/photo/racks-102/", "avg_color": "#FFFFFF",
         "alt": "server racks", "photographer": "Ben Ode",
         "photographer_url": "https://www.pexels.com/@ben",
         "src": {"landscape": "https://images.pexels.com/photos/102/p.jpeg"}}
      ]}
      """;

  private void searchReturns(String body) {
    api.stubFor(
        get(urlPathEqualTo("/v1/search"))
            .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(body)));
  }

  private Optional<StoryImage> imageOf(UUID id) {
    return Optional.ofNullable(new ImageQueries(images).forStories(List.of(id)).get(id));
  }

  private String status(UUID id) {
    return jdbc.queryForObject(
        "select status from story_images where item_id = ?", String.class, id);
  }

  @Test
  void picksACreditedPexelsPhoto() { // 009 R2.1–R2.5, R4.1, R5.1
    searchReturns(RESULTS);
    UUID id = seed.published("src", Section.GLOBAL_AI_TECH, NOW.minusSeconds(60));

    job("key-1", 40, List.of()).runOnce();

    StoryImage image = imageOf(id).orElseThrow();
    assertThat(image.kind()).isEqualTo("photo");
    assertThat(image.url())
        .isEqualTo("https://images.pexels.com/photos/101/p.jpeg?fit=crop&h=627&w=1200");
    assertThat(image.credit()).isEqualTo("Asha Rao");
    assertThat(image.creditUrl()).isEqualTo("https://www.pexels.com/@asha");
    assertThat(image.provider()).isEqualTo("Pexels");
    assertThat(image.providerUrl()).isEqualTo("https://www.pexels.com");
    assertThat(image.alt()).isEqualTo("a computer chip on a board");
    assertThat(image.color()).isEqualTo("#0C2340");
    api.verify(
        getRequestedFor(urlPathEqualTo("/v1/search"))
            .withQueryParam("query", equalTo("computer chip circuit board")) // GPU key term
            .withQueryParam("orientation", equalTo("landscape"))
            .withHeader("Authorization", equalTo("key-1")));
  }

  @Test
  void doesNotRepeatAPhotoUsedThisWeek() { // 009 R2.4
    searchReturns(RESULTS);
    UUID first = seed.published("src", Section.GLOBAL_AI_TECH, NOW.minusSeconds(60));
    UUID second = seed.published("src", Section.GLOBAL_AI_TECH, NOW.minusSeconds(120));

    job("key-1", 40, List.of()).runOnce();

    assertThat(imageOf(first).orElseThrow().credit()).isEqualTo("Asha Rao");
    assertThat(imageOf(second).orElseThrow().credit()).isEqualTo("Ben Ode");
  }

  @Test
  void usesThePublishersOwnImageOnlyWithRecordedPermission() { // 009 R3.2, R3.3
    ImagePermission permission =
        new ImagePermission(
            ImagePermission.Basis.PRESS_KIT,
            URI.create("https://ok.example/press"),
            LocalDate.parse("2026-10-07"),
            "OK Lab press kit");
    UUID allowed = seed.published("ok", Section.GLOBAL_AI_TECH, NOW.minusSeconds(60));
    UUID notAllowed = seed.published("nope", Section.GLOBAL_AI_TECH, NOW.minusSeconds(60));
    feedImages.insertIfNew(allowed, "https://ok.example/img.png", NOW);
    feedImages.insertIfNew(notAllowed, "https://nope.example/img.png", NOW);

    job(null, 40, List.of(source("ok", permission), source("nope", null))).runOnce();

    StoryImage image = imageOf(allowed).orElseThrow();
    assertThat(image.kind()).isEqualTo("publisher");
    assertThat(image.url()).isEqualTo("https://ok.example/img.png");
    assertThat(image.credit()).isEqualTo("OK Lab press kit");
    assertThat(image.providerUrl()).isEqualTo("https://ok.example/");
    assertThat(imageOf(notAllowed)).isEmpty(); // waits for a photo library; cover art meanwhile
    api.verify(0, getRequestedFor(urlPathEqualTo("/v1/search")));
  }

  @Test
  void marksNoneWhenNothingSafeIsFound() { // 009 R4.2
    searchReturns("{\"photos\": []}");
    UUID id = seed.published("src", Section.WORLD_BUSINESS, NOW.minusSeconds(60));

    job("key-1", 40, List.of()).runOnce();

    assertThat(status(id)).isEqualTo("NONE");
    assertThat(imageOf(id)).isEmpty();
  }

  @Test
  void retriesAFailedSearchThenGivesUp() { // 009 R4.3
    api.stubFor(get(urlPathEqualTo("/v1/search")).willReturn(aResponse().withStatus(503)));
    UUID id = seed.published("src", Section.WORLD_BUSINESS, NOW.minusSeconds(60));
    ImageJob job = job("key-1", 40, List.of());

    job.runOnce();
    assertThat(status(id)).isEqualTo("RETRY");
    job.runOnce(); // not due yet
    api.verify(1, getRequestedFor(urlPathEqualTo("/v1/search")));

    clock.advance(Duration.ofMinutes(31));
    job.runOnce();
    clock.advance(Duration.ofHours(4));
    job.runOnce();

    assertThat(status(id)).isEqualTo("NONE");
  }

  @Test
  void staysWithinTheHourlyBudget() { // 009 R2.6
    searchReturns(RESULTS);
    for (int i = 0; i < 3; i++) {
      seed.published("src", Section.GLOBAL_AI_TECH, NOW.minusSeconds(60 + i));
    }

    job("key-1", 2, List.of()).runOnce();

    api.verify(2, getRequestedFor(urlPathEqualTo("/v1/search")));
    assertThat(jdbc.queryForObject("select count(*) from story_images", Integer.class))
        .isEqualTo(2);
  }

  @Test
  void searchesNothingWithoutAnAccessKey() { // 009 R2.7
    seed.published("src", Section.GLOBAL_AI_TECH, NOW.minusSeconds(60));

    job("", 40, List.of()).runOnce();

    api.verify(0, getRequestedFor(urlPathEqualTo("/v1/search")));
    assertThat(jdbc.queryForObject("select count(*) from story_images", Integer.class)).isZero();
  }

  @Test
  void theApiCarriesThePicture() { // 009 R5.1
    searchReturns(RESULTS);
    UUID id = seed.published("src", Section.GLOBAL_AI_TECH, NOW.minusSeconds(60));
    job("key-1", 40, List.of()).runOnce();

    Map<UUID, StoryImage> found = new ImageQueries(images).forStories(List.of(id));

    assertThat(found).containsKey(id);
  }
}
