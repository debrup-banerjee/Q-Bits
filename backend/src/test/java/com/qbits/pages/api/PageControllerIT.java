package com.qbits.pages.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.qbits.ApiTest;
import com.qbits.catalog.CatalogFixtures;
import com.qbits.ingestion.persistence.ItemRepository;
import com.qbits.stories.domain.Section;
import com.qbits.stories.persistence.StoryRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class PageControllerIT extends ApiTest {

  private static final String SITE = "https://qbitsnews.example";
  private static final Pattern INITIAL =
      Pattern.compile(
          "<script id=\"qbits-initial-data\" type=\"application/json\">(.*?)</script>",
          Pattern.DOTALL);

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
  void storyPageCarriesTheStoryAndItsMetadata() { // 007 R1.1, R1.6, R1.7, R2.1–R2.5, R7.1
    UUID id = seed.published("example-lab", Section.INDIA_AI, now.minus(Duration.ofHours(1)));

    Result r = get("/story/" + id);

    assertThat(r.status).isEqualTo(200);
    assertThat(r.contentType).startsWith("text/html");
    assertThat(r.cacheControl).contains("max-age=300").contains("public");
    assertThat(r.body)
        .contains("<title>Headline " + id + " | Q-Bits</title>")
        .contains("<meta name=\"description\" content=\"Our summary.\" />")
        .contains("<link rel=\"canonical\" href=\"" + SITE + "/story/" + id + "\" />")
        .contains("<meta property=\"og:type\" content=\"article\" />")
        .contains("\"@type\":\"NewsArticle\"")
        .contains("\"isBasedOn\":\"https://news.example/" + id + "\"")
        .contains(">Headline " + id + "</a></h1>")
        .contains("Read the full story at Example Lab")
        .contains("src=\"/assets/index-test.js\"")
        .doesNotContain("SOURCE TITLE", "SOURCE EXCERPT", "noindex");
  }

  @Test
  void storyInitialDataEqualsTheApiResponse() { // 007 R4.1
    UUID id = seed.published("example-lab", Section.INDIA_AI, now.minus(Duration.ofHours(1)));

    JsonNode data = initialData(get("/story/" + id).body);

    assertThat(data.get("path").asString()).isEqualTo("/story/" + id);
    JsonNode q = data.get("queries").get(0);
    assertThat(q.get("key").toString()).isEqualTo("[\"story\",\"" + id + "\"]");
    assertThat(q.get("infinite").asBoolean()).isFalse();
    assertThat(q.get("data")).isEqualTo(api("/api/v1/stories/" + id));
  }

  @Test
  void missingStoryIsANoindex404() { // 007 R3.1, R7.1
    Result missing = get("/story/" + UUID.randomUUID());
    Result malformed = get("/story/not-a-uuid");

    for (Result r : List.of(missing, malformed)) {
      assertThat(r.status).isEqualTo(404);
      assertThat(r.contentType).startsWith("text/html");
      assertThat(r.cacheControl).contains("no-store");
      assertThat(r.body)
          .contains("<title>Story not available | Q-Bits</title>")
          .contains("<meta name=\"robots\" content=\"noindex\" />")
          .contains("<div id=\"root\"></div>")
          .doesNotContain("qbits-initial-data");
    }
  }

  @Test
  void expiredStoryIsA404() { // 007 R3.1
    UUID id = seed.published("example-lab", Section.INDIA_AI, now.minus(Duration.ofHours(80)));

    assertThat(get("/story/" + id).status).isEqualTo(404);
  }

  @Test
  void homeShowsTheLatestEdition() { // 007 R1.3, R2.1, R2.6, R4.1
    UUID id = seed.published("example-lab", Section.WORLD_BUSINESS, now.minus(Duration.ofHours(2)));
    UUID old =
        seed.published("example-lab", Section.WORLD_BUSINESS, now.minus(Duration.ofHours(30)));

    Result r = get("/");

    assertThat(r.status).isEqualTo(200);
    assertThat(r.body)
        .contains("<title>Q-Bits: today&#39;s AI news, explained in plain words</title>")
        .contains("<link rel=\"canonical\" href=\"" + SITE + "/\" />")
        .contains("\"@type\":\"WebSite\"", "\"@type\":\"Organization\"")
        .contains(">AI Latest</h1>")
        .contains("Headline " + id)
        .doesNotContain("Headline " + old);
    JsonNode q = initialData(r.body).get("queries").get(0);
    assertThat(q.get("key").toString()).isEqualTo("[\"stories\",\"all\",20,24]");
    assertThat(q.get("infinite").asBoolean()).isTrue();
    assertThat(q.get("data")).isEqualTo(api("/api/v1/stories?limit=20&hours=24"));
  }

  @Test
  void sectionPageShowsItsStories() { // 007 R1.2, R2.1, R4.1
    UUID id = seed.published("example-lab", Section.INDIA_AI, now.minus(Duration.ofHours(1)));
    UUID other =
        seed.published("example-lab", Section.WORLD_BUSINESS, now.minus(Duration.ofHours(1)));

    Result r = get("/section/india-ai");

    assertThat(r.status).isEqualTo(200);
    assertThat(r.body)
        .contains("<title>India AI: AI news from the last 72 hours | Q-Bits</title>")
        .contains("<link rel=\"canonical\" href=\"" + SITE + "/section/india-ai\" />")
        .contains(">India AI</h1>")
        .contains("Headline " + id)
        .doesNotContain("Headline " + other);
    JsonNode queries = initialData(r.body).get("queries");
    assertThat(queries.get(0).get("key").toString()).isEqualTo("[\"sections\"]");
    assertThat(queries.get(0).get("data")).isEqualTo(api("/api/v1/sections"));
    assertThat(queries.get(1).get("key").toString()).isEqualTo("[\"stories\",\"india-ai\",20,72]");
    assertThat(queries.get(1).get("data"))
        .isEqualTo(api("/api/v1/stories?section=india-ai&limit=20"));
  }

  @Test
  void unknownSectionIsANoindex404() { // 007 R3.2
    Result r = get("/section/sports");

    assertThat(r.status).isEqualTo(404);
    assertThat(r.body).contains("noindex", "<title>Page not found | Q-Bits</title>");
  }

  @Test
  void overviewShowsEverySection() { // 007 R1.4, R4.1
    UUID id = seed.published("example-lab", Section.INDIA_AI, now.minus(Duration.ofHours(1)));

    Result r = get("/sections");

    assertThat(r.status).isEqualTo(200);
    assertThat(r.body).contains("<title>AI news by section | Q-Bits</title>", "Headline " + id);
    for (Section s : Section.values()) {
      assertThat(r.body).contains("href=\"/section/" + s.slug() + "\"");
    }
    JsonNode queries = initialData(r.body).get("queries");
    assertThat(queries).hasSize(1 + Section.values().length);
    assertThat(queries.get(4).get("key").toString()).isEqualTo("[\"stories\",\"india-ai\",5,72]");
    assertThat(queries.get(4).get("data"))
        .isEqualTo(api("/api/v1/stories?section=india-ai&limit=5"));
  }

  @Test
  void aboutIsIndexableAndAccountPagesAreNot() { // 007 R1.5, R2.8
    Result about = get("/about");
    Result login = get("/login");
    Result register = get("/register");

    assertThat(about.status).isEqualTo(200);
    assertThat(about.body).contains("<title>About Q-Bits</title>").doesNotContain("noindex");
    assertThat(login.status).isEqualTo(200);
    assertThat(login.body).contains("<title>Log in | Q-Bits</title>", "noindex");
    assertThat(register.status).isEqualTo(200);
    assertThat(register.body).contains("<title>Create an account | Q-Bits</title>", "noindex");
  }

  @Test
  void unknownPathsAreANoindex404() { // 007 R3.3
    Result r = get("/no/such/page");

    assertThat(r.status).isEqualTo(404);
    assertThat(r.contentType).startsWith("text/html");
    assertThat(r.body).contains("noindex", "src=\"/assets/index-test.js\"");
  }

  @Test
  void apiPathsStayJson() { // 007 R3.3, conventions: the API returns data, never HTML
    Result unknown = get("/api/v1/nope");
    Result sections = get("/api/v1/sections");

    assertThat(unknown.status).isEqualTo(404);
    assertThat(unknown.contentType).startsWith("application/problem+json");
    assertThat(sections.status).isEqualTo(200);
    assertThat(sections.contentType).startsWith("application/json");
  }

  @Test
  void everyWebAppRouteIsServed() throws Exception { // 007 Risks: a new route must not 404
    UUID id = seed.published("example-lab", Section.INDIA_AI, now.minus(Duration.ofHours(1)));
    String routes = Files.readString(Path.of("..", "web", "src", "app", "AppRoutes.tsx"));
    List<String> paths = new ArrayList<>();
    if (routes.contains("<Route index ")) {
      paths.add("/");
    }
    Matcher m = Pattern.compile("path=\"([^\"*]+)\"").matcher(routes);
    while (m.find()) {
      paths.add("/" + m.group(1).replace(":slug", "india-ai").replace(":id", id.toString()));
    }

    assertThat(paths).contains("/", "/sections", "/about", "/story/" + id);
    for (String p : paths) {
      assertThat(get(p).status).as(p).isEqualTo(200);
    }
  }

  private JsonNode initialData(String html) {
    Matcher m = INITIAL.matcher(html);
    assertThat(m.find()).as("initial data element").isTrue();
    return json.readTree(m.group(1));
  }

  private JsonNode api(String path) {
    return json.readTree(get(path).body);
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
                    new String(
                        res.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)));
  }
}
