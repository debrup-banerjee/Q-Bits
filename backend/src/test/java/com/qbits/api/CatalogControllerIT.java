package com.qbits.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.qbits.ApiTest;
import com.qbits.catalog.CatalogFixtures;
import com.qbits.ingestion.persistence.ItemRepository;
import com.qbits.stories.domain.Section;
import com.qbits.stories.persistence.StoryRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class CatalogControllerIT extends ApiTest {

  @Autowired ItemRepository items;
  @Autowired StoryRepository stories;
  private final JsonMapper json = JsonMapper.builder().build();
  private CatalogFixtures seed;
  private Instant now;

  @BeforeEach
  void setUp() {
    seed = new CatalogFixtures(items, stories);
    now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
  }

  @Test
  void listsSectionsInOrderWithCounts() { // 003 R1.1
    seed.published("example-lab", Section.INDIA_AI, now.minus(Duration.ofHours(1)));

    JsonNode body = get("/api/v1/sections").body;

    assertThat(body.findValuesAsString("slug"))
        .containsExactly("global-ai-tech", "world-business", "india-ai", "innovations-research");
    assertThat(body.get(2).get("name").asString()).isEqualTo("India AI");
    assertThat(body.get(2).get("storyCount").asLong()).isEqualTo(1);
    assertThat(body.get(0).get("storyCount").asLong()).isZero();
  }

  @Test
  void listsStoriesWithClientFields() { // 003 R2.1, R2.3, R2.4
    UUID id = seed.published("example-lab", Section.INDIA_AI, now.minus(Duration.ofHours(1)));

    Result r = get("/api/v1/stories?section=india-ai&limit=20");

    JsonNode story = r.body.get("data").get(0);
    assertThat(story.get("id").asString()).isEqualTo(id.toString());
    assertThat(story.get("section").get("slug").asString()).isEqualTo("india-ai");
    assertThat(story.get("source").get("name").asString()).isEqualTo("Example Lab");
    assertThat(story.get("source").get("homepage").asString()).isEqualTo("https://lab.example.org");
    assertThat(story.get("attribution").asString())
        .isEqualTo("Summary written from Example Lab's headline and teaser");
    assertThat(story.get("keyTerms").get(0).get("term").asString()).isEqualTo("GPU");
    assertThat(story.has("dateEstimated")).isTrue();
    assertThat(r.body.has("nextCursor")).isTrue();
    assertThat(r.raw).doesNotContain("SOURCE TITLE", "SOURCE EXCERPT");
  }

  @Test
  void pagesWithCursor() { // 003 R2.1
    for (int i = 0; i < 3; i++) {
      seed.published("example-lab", Section.WORLD_BUSINESS, now.minus(Duration.ofMinutes(i + 1)));
    }
    Result first = get("/api/v1/stories?limit=2");
    String cursor = first.body.get("nextCursor").asString();

    Result second = get("/api/v1/stories?limit=2&cursor=" + cursor);

    assertThat(first.body.get("data")).hasSize(2);
    assertThat(second.body.get("data")).hasSize(1);
    assertThat(second.body.get("nextCursor").isNull()).isTrue();
  }

  @Test
  void unknownSectionIsProblem400() { // 003 R2.5
    Result r = get("/api/v1/stories?section=business");

    assertThat(r.status).isEqualTo(400);
    assertThat(r.contentType).startsWith("application/problem+json");
    assertThat(r.body.get("code").asString()).isEqualTo("UNKNOWN_SECTION");
  }

  @Test
  void badCursorAndLimitAreProblem400() {
    assertThat(get("/api/v1/stories?cursor=zzz").body.get("code").asString())
        .isEqualTo("INVALID_CURSOR");
    assertThat(get("/api/v1/stories?limit=101").body.get("code").asString())
        .isEqualTo("INVALID_LIMIT");
    assertThat(get("/api/v1/stories?limit=0").status).isEqualTo(400);
  }

  @Test
  void singleStoryOr404() { // 003 R2.6
    UUID id = seed.published("example-lab", Section.INDIA_AI, now.minus(Duration.ofHours(1)));
    UUID old = seed.published("example-lab", Section.INDIA_AI, now.minus(Duration.ofHours(73)));

    assertThat(get("/api/v1/stories/" + id).body.get("headline").asString())
        .startsWith("Headline ");
    Result missing = get("/api/v1/stories/" + old);
    assertThat(missing.status).isEqualTo(404);
    assertThat(missing.body.get("code").asString()).isEqualTo("STORY_NOT_FOUND");
    assertThat(get("/api/v1/stories/not-a-uuid").status).isEqualTo(400);
  }

  @Test
  void sendsCacheAndDataAsOfHeaders() { // 003 R2.7
    Instant latest = now.minus(Duration.ofMinutes(5));
    seed.published("example-lab", Section.INDIA_AI, latest);

    Result r = get("/api/v1/stories");

    assertThat(r.cacheControl).isEqualTo("max-age=60, public");
    assertThat(Instant.parse(r.dataAsOf)).isEqualTo(latest);
  }

  @Test
  void listsEnabledSourcesAndSiteContact() { // 003 R3.1, R7.3
    JsonNode sources = get("/api/v1/sources").body;
    assertThat(sources).hasSize(1);
    assertThat(sources.get(0).propertyNames()).containsExactlyInAnyOrder("name", "homepage");

    JsonNode site = get("/api/v1/site").body;
    assertThat(site.get("name").asString()).isEqualTo("Q-Bits");
    assertThat(site.get("contactEmail").asString()).isEqualTo("debrup28.nitdgp@gmail.com");
  }

  @Test
  void corsAllowsOnlyConfiguredOrigin() {
    ResponseEntity<String> ok =
        http.get()
            .uri("/api/v1/sections")
            .header("Origin", "http://localhost:5173")
            .retrieve()
            .toEntity(String.class);
    assertThat(ok.getHeaders().getAccessControlAllowOrigin()).isEqualTo("http://localhost:5173");
    assertThat(ok.getHeaders().getAccessControlExposeHeaders()).contains("X-Data-As-Of");

    int status =
        http.get()
            .uri("/api/v1/sections")
            .header("Origin", "https://evil.example")
            .exchange((req, res) -> res.getStatusCode().value());
    assertThat(status).isEqualTo(403);
  }

  private record Result(
      int status,
      String contentType,
      String cacheControl,
      String dataAsOf,
      String raw,
      JsonNode body) {}

  private Result get(String path) {
    return http.get()
        .uri(path)
        .exchange(
            (req, res) -> {
              String raw = new String(res.getBody().readAllBytes());
              return new Result(
                  res.getStatusCode().value(),
                  String.valueOf(res.getHeaders().getContentType()),
                  res.getHeaders().getCacheControl(),
                  res.getHeaders().getFirst("X-Data-As-Of"),
                  raw,
                  raw.isEmpty() ? null : json.readTree(raw));
            });
  }
}
