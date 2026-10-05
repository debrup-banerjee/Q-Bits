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
  @Autowired com.qbits.resources.persistence.ItemResourceRepository itemResources;
  @Autowired com.qbits.stories.digest.EditionRepository editionRepo;
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
        .containsExactly(
            "global-ai-tech", "new-releases", "world-business", "india-ai", "innovations-research");
    assertThat(body.get(3).get("name").asString()).isEqualTo("India AI");
    assertThat(body.get(3).get("storyCount").asLong()).isEqualTo(1);
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
  void includesVerifiedOpenSourceLinksInOrderAndAtMostThree() { // 005 R5.1, R5.2
    UUID id = seed.published("example-lab", Section.GLOBAL_AI_TECH, now.minus(Duration.ofHours(1)));
    link(id, "https://arxiv.org/abs/2410.01234", true);
    link(id, "https://huggingface.co/datasets/allenai/c4", true);
    link(id, "https://huggingface.co/kestrel-ai/Kestrel-70B", true);
    link(id, "https://github.com/kestrel-ai/kestrel", true);
    link(id, "https://github.com/kestrel-ai/unverified", false);

    JsonNode resources = get("/api/v1/stories/" + id).body.get("resources");

    assertThat(resources).hasSize(3);
    assertThat(resources.findValuesAsString("type")).containsExactly("code", "model", "dataset");
    assertThat(resources.findValuesAsString("label"))
        .containsExactly("Code on GitHub", "Model on Hugging Face", "Dataset on Hugging Face");
    assertThat(resources.get(0).get("name").asString()).isEqualTo("kestrel-ai/kestrel");
    assertThat(resources.get(0).get("url").asString())
        .isEqualTo("https://github.com/kestrel-ai/kestrel");
  }

  @Test
  void storyListIncludesVerifiedOpenSourceLinksToo() { // 005 R5.1, R5.2
    UUID withLinks =
        seed.published("example-lab", Section.GLOBAL_AI_TECH, now.minus(Duration.ofHours(1)));
    UUID without =
        seed.published("example-lab", Section.GLOBAL_AI_TECH, now.minus(Duration.ofHours(2)));
    link(withLinks, "https://arxiv.org/abs/2410.01234", true);
    link(withLinks, "https://huggingface.co/datasets/allenai/c4", true);
    link(withLinks, "https://huggingface.co/kestrel-ai/Kestrel-70B", true);
    link(withLinks, "https://github.com/kestrel-ai/kestrel", true);
    link(withLinks, "https://github.com/kestrel-ai/unverified", false);

    JsonNode data = get("/api/v1/stories?section=global-ai-tech").body.get("data");

    JsonNode first = data.get(0);
    assertThat(first.get("id").asString()).isEqualTo(withLinks.toString());
    JsonNode resources = first.get("resources");
    assertThat(resources).hasSize(3);
    assertThat(resources.findValuesAsString("type")).containsExactly("code", "model", "dataset");
    assertThat(resources.findValuesAsString("label"))
        .containsExactly("Code on GitHub", "Model on Hugging Face", "Dataset on Hugging Face");
    assertThat(resources.findValuesAsString("url"))
        .containsExactly(
            "https://github.com/kestrel-ai/kestrel",
            "https://huggingface.co/kestrel-ai/Kestrel-70B",
            "https://huggingface.co/datasets/allenai/c4");
    assertThat(resources.findValuesAsString("name"))
        .containsExactly("kestrel-ai/kestrel", "kestrel-ai/Kestrel-70B", "allenai/c4");
    JsonNode second = data.get(1);
    assertThat(second.get("id").asString()).isEqualTo(without.toString());
    assertThat(second.get("resources")).isEmpty();
  }

  @Test
  void storiesWithoutLinksHaveAnEmptyList() { // 005 R5.1
    seed.published("example-lab", Section.GLOBAL_AI_TECH, now.minus(Duration.ofHours(1)));

    JsonNode story = get("/api/v1/stories").body.get("data").get(0);

    assertThat(story.get("resources").isArray()).isTrue();
    assertThat(story.get("resources")).isEmpty();
  }

  private void link(UUID itemId, String url, boolean verified) {
    var c = new com.qbits.resources.domain.ResourceLinkNormaliser().normalise(url).orElseThrow();
    itemResources.insertIfNew(itemId, c, com.qbits.common.links.LinkOrigin.DESCRIPTION, now);
    if (verified) {
      long rid =
          itemResources.findByItem(itemId).stream()
              .filter(r -> r.url().equals(c.url()))
              .findFirst()
              .orElseThrow()
              .id();
      itemResources.markVerified(rid, c.url(), c.name(), now);
    }
  }

  @Test
  void windowsAreAnchoredToTheLatestEditionCutoff() { // 006 R4.1, R4.2
    Instant cutoff = now.minus(Duration.ofHours(10));
    publishedEdition(cutoff, now.minus(Duration.ofHours(9)), 2);
    UUID inside =
        seed.published("example-lab", Section.INDIA_AI, cutoff.minus(Duration.ofHours(20)));
    UUID outside =
        seed.published("example-lab", Section.INDIA_AI, cutoff.minus(Duration.ofHours(25)));

    JsonNode latest = get("/api/v1/stories?hours=24").body.get("data");

    // 'inside' is 30 h old by the clock but within 24 h of the edition's cut-off, so it stays.
    assertThat(latest.findValuesAsString("id")).containsExactly(inside.toString());
    assertThat(get("/api/v1/stories").body.get("data").findValuesAsString("id"))
        .containsExactly(inside.toString(), outside.toString());
  }

  @Test
  void editionEndpointAndDataAsOf() { // 006 R5.1, R5.2
    assertThat(get("/api/v1/edition").status).isEqualTo(404);
    assertThat(get("/api/v1/edition").body.get("code").asString()).isEqualTo("NO_EDITION");

    Instant cutoff = now.minus(Duration.ofHours(3));
    Instant published = now.minus(Duration.ofHours(2));
    UUID id = publishedEdition(cutoff, published, 7);

    Result r = get("/api/v1/edition");
    assertThat(r.body.get("id").asString()).isEqualTo(id.toString());
    assertThat(r.body.get("storyCount").asInt()).isEqualTo(7);
    assertThat(Instant.parse(r.body.get("nextCutoffAt").asString())).isAfter(now);
    assertThat(r.body.get("late").isBoolean()).isTrue();
    assertThat(Instant.parse(get("/api/v1/stories").dataAsOf)).isEqualTo(published);
  }

  private UUID publishedEdition(Instant cutoff, Instant publishedAt, int count) {
    UUID id = com.qbits.common.Ids.newId();
    editionRepo.create(
        new com.qbits.stories.digest.Edition(
            id,
            cutoff,
            com.qbits.stories.digest.EditionStatus.COLLECTING,
            null,
            null,
            0,
            0,
            0,
            0,
            cutoff,
            null,
            cutoff,
            null));
    editionRepo.markPublished(id, count, 0, 0, publishedAt);
    return id;
  }

  @Test
  void hoursLimitsTheWindow() { // 004 R1.1
    UUID recent = seed.published("example-lab", Section.INDIA_AI, now.minus(Duration.ofHours(23)));
    UUID older = seed.published("example-lab", Section.INDIA_AI, now.minus(Duration.ofHours(25)));

    JsonNode latest = get("/api/v1/stories?hours=24").body.get("data");
    JsonNode all = get("/api/v1/stories").body.get("data");

    assertThat(latest.findValuesAsString("id")).containsExactly(recent.toString());
    assertThat(all.findValuesAsString("id")).containsExactly(recent.toString(), older.toString());
  }

  @Test
  void aStoryExactly24HoursOldIsInTheLatestWindow() { // 004 R1.1
    Instant cutoff = now.minus(Duration.ofHours(2));
    publishedEdition(cutoff, now.minus(Duration.ofHours(1)), 2);
    Instant edge = cutoff.minus(Duration.ofHours(24));
    UUID atEdge = seed.published("example-lab", Section.INDIA_AI, edge);
    seed.published("example-lab", Section.INDIA_AI, edge.minusMillis(1));

    JsonNode latest = get("/api/v1/stories?hours=24").body.get("data");

    assertThat(latest.findValuesAsString("id")).containsExactly(atEdge.toString());
  }

  @Test
  void hoursCombinesWithSectionAndCursor() { // 004 R1.3
    for (int i = 0; i < 3; i++) {
      seed.published("example-lab", Section.WORLD_BUSINESS, now.minus(Duration.ofHours(i + 1)));
    }
    seed.published("example-lab", Section.INDIA_AI, now.minus(Duration.ofMinutes(30)));
    seed.published("example-lab", Section.WORLD_BUSINESS, now.minus(Duration.ofHours(30)));

    Result first = get("/api/v1/stories?hours=24&section=world-business&limit=2");
    Result second =
        get(
            "/api/v1/stories?hours=24&section=world-business&limit=2&cursor="
                + first.body.get("nextCursor").asString());

    assertThat(first.body.get("data")).hasSize(2);
    assertThat(second.body.get("data")).hasSize(1);
    assertThat(second.body.get("nextCursor").isNull()).isTrue();
  }

  @Test
  void invalidHoursIsProblem400() { // 004 R1.2
    for (String bad : new String[] {"0", "73", "abc", "1.5"}) {
      Result r = get("/api/v1/stories?hours=" + bad);
      assertThat(r.status).as(bad).isEqualTo(400);
      assertThat(r.body.get("code").asString()).isEqualTo("INVALID_HOURS");
    }
  }

  @Test
  void sectionCountsStay72Hours() { // 004 R1.4
    seed.published("example-lab", Section.INDIA_AI, now.minus(Duration.ofHours(30)));

    assertThat(get("/api/v1/sections").body.get(3).get("storyCount").asLong()).isEqualTo(1);
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
