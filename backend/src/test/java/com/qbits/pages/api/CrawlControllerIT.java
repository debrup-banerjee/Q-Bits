package com.qbits.pages.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.qbits.ApiTest;
import com.qbits.catalog.CatalogFixtures;
import com.qbits.ingestion.persistence.ItemRepository;
import com.qbits.stories.domain.Section;
import com.qbits.stories.persistence.StoryRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CrawlControllerIT extends ApiTest {

  private static final String SITE = "https://qbitsnews.example";

  @Autowired ItemRepository items;
  @Autowired StoryRepository stories;
  private CatalogFixtures seed;
  private Instant now;

  @BeforeEach
  void setUp() {
    seed = new CatalogFixtures(items, stories);
    now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
  }

  @Test
  void robotsIsPlainTextAndNamesTheSitemaps() { // 007 R6.1, R7.2
    Result r = get("/robots.txt");

    assertThat(r.status).isEqualTo(200);
    assertThat(r.contentType).startsWith("text/plain");
    assertThat(r.cacheControl).contains("max-age=3600").contains("public");
    assertThat(r.body)
        .contains("Disallow: /api/")
        .contains("Sitemap: " + SITE + "/sitemap.xml")
        .contains("Sitemap: " + SITE + "/sitemap-news.xml");
  }

  @Test
  void sitemapListsPagesSectionsAndCurrentStories() { // 007 R6.2, R7.2
    UUID fresh = seed.published("example-lab", Section.INDIA_AI, now.minus(Duration.ofHours(1)));
    UUID expired = seed.published("example-lab", Section.INDIA_AI, now.minus(Duration.ofHours(80)));

    Result r = get("/sitemap.xml");

    assertThat(r.status).isEqualTo(200);
    assertThat(r.contentType).startsWith("application/xml");
    assertThat(r.cacheControl).contains("max-age=3600");
    assertThat(r.body)
        .startsWith("<?xml")
        .contains("<loc>" + SITE + "/</loc>")
        .contains("<loc>" + SITE + "/sections</loc>")
        .contains("<loc>" + SITE + "/about</loc>")
        .contains("<loc>" + SITE + "/story/" + fresh + "</loc>")
        .doesNotContain(expired.toString());
    for (Section s : Section.values()) {
      assertThat(r.body).contains("<loc>" + SITE + "/section/" + s.slug() + "</loc>");
    }
  }

  @Test
  void sitemapPagesThroughEveryStory() { // 007 R6.2
    for (int i = 0; i < CrawlController.PAGE_SIZE + 5; i++) {
      seed.published("example-lab", Section.WORLD_BUSINESS, now.minus(Duration.ofMinutes(i + 1)));
    }

    Result r = get("/sitemap.xml");

    assertThat(r.body.split("/story/", -1)).hasSize(CrawlController.PAGE_SIZE + 5 + 1);
  }

  @Test
  void newsSitemapHasOnlyTheLastTwoDays() { // 007 R6.3
    UUID fresh = seed.published("example-lab", Section.INDIA_AI, now.minus(Duration.ofHours(3)));
    UUID older = seed.published("example-lab", Section.INDIA_AI, now.minus(Duration.ofHours(50)));

    Result r = get("/sitemap-news.xml");

    assertThat(r.status).isEqualTo(200);
    assertThat(r.body)
        .contains("xmlns:news=\"http://www.google.com/schemas/sitemap-news/0.9\"")
        .contains("<loc>" + SITE + "/story/" + fresh + "</loc>")
        .contains("<news:title>Headline " + fresh + "</news:title>")
        .doesNotContain(older.toString());
  }

  private record Result(int status, String contentType, String cacheControl, String body) {}

  private Result get(String path) {
    return http.get()
        .uri(path)
        .exchange(
            (req, res) ->
                new Result(
                    res.getStatusCode().value(),
                    String.valueOf(res.getHeaders().getContentType()),
                    String.valueOf(res.getHeaders().getCacheControl()),
                    new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)));
  }
}
