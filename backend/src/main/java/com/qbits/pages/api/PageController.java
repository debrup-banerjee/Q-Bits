package com.qbits.pages.api;

import com.qbits.api.EditionController.EditionView;
import com.qbits.catalog.CatalogService;
import com.qbits.catalog.domain.StoryPage;
import com.qbits.catalog.domain.StoryView;
import com.qbits.common.AppException;
import com.qbits.pages.PageShell;
import com.qbits.pages.domain.JsonLd;
import com.qbits.pages.domain.PageContent;
import com.qbits.pages.domain.PageModel;
import com.qbits.pages.domain.PageRenderer;
import com.qbits.pages.domain.PageTitles;
import com.qbits.stories.digest.EditionQueries;
import com.qbits.stories.domain.Section;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import tools.jackson.databind.json.JsonMapper;

/**
 * The first HTML response for each web app route, filled with the page's metadata, readable content
 * and the API data it was built from (spec 007 R1–R4, R7.1). The web app then starts as a
 * single-page app. HTML lives here, outside {@code /api} (conventions).
 */
@Controller
public class PageController {

  private static final Logger log = LoggerFactory.getLogger(PageController.class);

  /** The page sizes and windows the web app's hooks use, so the seeded query keys match. */
  static final int LATEST_PAGE = 20;

  static final int LATEST_HOURS = 24;
  static final int SECTION_PAGE = 20;
  static final int OVERVIEW_PER_SECTION = 5;
  static final int WINDOW_HOURS = 72;

  private static final MediaType HTML = MediaType.parseMediaType("text/html;charset=UTF-8");
  private static final CacheControl CACHED =
      CacheControl.maxAge(Duration.ofSeconds(300)).cachePublic();

  /** One seeded TanStack query: its key, the API body, and that response's data-as-of time. */
  record Query(List<Object> key, boolean infinite, Object data, String dataAsOf) {}

  /** The initial data element's content (R4.1). */
  record InitialData(String path, List<Query> queries) {}

  /** The {@code GET /api/v1/sections} item shape. */
  record SectionData(String slug, String name, String description, long storyCount) {}

  private final CatalogService catalog;
  private final EditionQueries editions;
  private final PageShell shell;
  private final JsonMapper json;

  public PageController(
      CatalogService catalog, EditionQueries editions, PageShell shell, JsonMapper json) {
    this.catalog = catalog;
    this.editions = editions;
    this.shell = shell;
    this.json = json;
  }

  @GetMapping("/")
  public ResponseEntity<String> home() {
    return respond(
        "/",
        () -> {
          StoryPage page = latest();
          List<Query> queries = new ArrayList<>();
          queries.add(new Query(storiesKey("all", LATEST_PAGE, LATEST_HOURS), true, page, asOf()));
          editions
              .latestPublished()
              .ifPresent(
                  e ->
                      queries.add(
                          new Query(
                              List.of("edition"),
                              false,
                              EditionView.of(e, editions),
                              e.publishedAt().toString())));
          String site = renderer().siteUrl();
          return PageModel.page(
              PageTitles.HOME,
              PageTitles.HOME_DESCRIPTION,
              "/",
              "website",
              List.of(write(JsonLd.webSite(site)), write(JsonLd.organization(site))),
              PageContent.latestPage(page.data()),
              initialData("/", queries));
        });
  }

  @GetMapping("/sections")
  public ResponseEntity<String> sections() {
    return respond(
        "/sections",
        () -> {
          String asOf = asOf();
          List<Query> queries = new ArrayList<>();
          queries.add(new Query(List.of("sections"), false, sectionData(), asOf));
          Map<Section, List<StoryView>> bySection = new EnumMap<>(Section.class);
          for (Section s : Section.values()) {
            StoryPage page = stories(s, OVERVIEW_PER_SECTION);
            bySection.put(s, page.data());
            queries.add(
                new Query(
                    storiesKey(s.slug(), OVERVIEW_PER_SECTION, WINDOW_HOURS), true, page, asOf));
          }
          return PageModel.page(
              PageTitles.SECTIONS,
              PageTitles.SECTIONS_DESCRIPTION,
              "/sections",
              "website",
              List.of(),
              PageContent.overviewPage(bySection),
              initialData("/sections", queries));
        });
  }

  @GetMapping("/section/{slug}")
  public ResponseEntity<String> section(@PathVariable String slug) {
    String path = "/section/" + slug;
    return respond(
        path,
        () -> {
          Optional<Section> section = Section.fromSlug(slug);
          if (section.isEmpty()) {
            return PageModel.appOnly(404, PageTitles.NOT_FOUND, PageTitles.NOT_FOUND_DESCRIPTION);
          }
          Section s = section.get();
          String asOf = asOf();
          StoryPage page = stories(s, SECTION_PAGE);
          return PageModel.page(
              PageTitles.section(s.displayName()),
              PageContent.describe(s.description()),
              path,
              "website",
              List.of(),
              PageContent.sectionPage(s, page.data()),
              initialData(
                  path,
                  List.of(
                      new Query(List.of("sections"), false, sectionData(), asOf),
                      new Query(
                          storiesKey(s.slug(), SECTION_PAGE, WINDOW_HOURS), true, page, asOf))));
        });
  }

  @GetMapping("/story/{id}")
  public ResponseEntity<String> story(@PathVariable String id) {
    String path = "/story/" + id;
    return respond(
        path,
        () -> {
          Optional<StoryView> story = parse(id).flatMap(catalog::find);
          if (story.isEmpty()) {
            return PageModel.appOnly(
                404, PageTitles.STORY_NOT_FOUND, PageTitles.NOT_FOUND_DESCRIPTION);
          }
          StoryView s = story.get();
          String canonical = "/story/" + s.id();
          String url = renderer().url(canonical);
          return PageModel.page(
                  PageTitles.story(s.headline()),
                  PageContent.describe(s.summary()),
                  canonical,
                  "article",
                  List.of(write(JsonLd.newsArticle(s, url, renderer().siteUrl()))),
                  PageContent.storyPage(s),
                  initialData(path, List.of(new Query(List.of("story", id), false, s, asOf()))))
              .withImage(s.image() == null ? "" : s.image().url());
        });
  }

  @GetMapping("/about")
  public ResponseEntity<String> about() {
    PageModel page =
        PageModel.page(
            PageTitles.ABOUT, PageTitles.ABOUT_DESCRIPTION, "/about", "website", List.of(), "", "");
    return send(page);
  }

  @GetMapping("/login")
  public ResponseEntity<String> login() {
    return send(PageModel.appOnly(200, PageTitles.LOGIN, PageTitles.HOME_DESCRIPTION));
  }

  @GetMapping("/register")
  public ResponseEntity<String> register() {
    return send(PageModel.appOnly(200, PageTitles.REGISTER, PageTitles.HOME_DESCRIPTION));
  }

  /**
   * Any other path: a 404 page the web app still loads into (R3.3). API, docs and actuator paths,
   * and anything but GET, keep the JSON Problem Details answer.
   */
  @RequestMapping("/**")
  public ResponseEntity<String> notFound(HttpServletRequest request) {
    String path = request.getRequestURI();
    boolean read = "GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod());
    if (!read
        || path.startsWith("/api/")
        || path.startsWith("/v3/")
        || path.startsWith("/actuator")) {
      throw new AppException("NOT_FOUND", HttpStatus.NOT_FOUND, "No such endpoint.");
    }
    return send(PageModel.appOnly(404, PageTitles.NOT_FOUND, PageTitles.NOT_FOUND_DESCRIPTION));
  }

  /** Builds the page; if the database fails, sends the plain shell so the app still loads. */
  private ResponseEntity<String> respond(String path, Supplier<PageModel> build) {
    try {
      return send(build.get());
    } catch (DataAccessException e) {
      log.error("page data unavailable path={} error={}", path, e.getClass().getSimpleName(), e);
      return send(PageModel.appOnly(503, PageRenderer.SITE_NAME, PageTitles.HOME_DESCRIPTION));
    }
  }

  private ResponseEntity<String> send(PageModel page) {
    return ResponseEntity.status(page.status())
        .contentType(HTML)
        .cacheControl(page.status() == 200 ? CACHED : CacheControl.noStore())
        .body(renderer().render(page));
  }

  private PageRenderer renderer() {
    return shell.renderer();
  }

  private StoryPage latest() {
    return catalog.list(
        Optional.empty(), Optional.empty(), LATEST_PAGE, Duration.ofHours(LATEST_HOURS));
  }

  private StoryPage stories(Section s, int limit) {
    return catalog.list(Optional.of(s), Optional.empty(), limit, Duration.ofHours(WINDOW_HOURS));
  }

  private List<SectionData> sectionData() {
    Map<Section, Long> counts = catalog.counts();
    return Arrays.stream(Section.values())
        .map(
            s ->
                new SectionData(
                    s.slug(), s.displayName(), s.description(), counts.getOrDefault(s, 0L)))
        .toList();
  }

  private String asOf() {
    return catalog.dataAsOf().map(Instant::toString).orElse(null);
  }

  /** The web app's {@code storiesKey(section, limit, hours)} (packages/api-client). */
  private static List<Object> storiesKey(String section, int limit, int hours) {
    return List.of("stories", section, limit, hours);
  }

  private String initialData(String path, List<Query> queries) {
    return write(new InitialData(path, queries));
  }

  private String write(Object value) {
    return json.writeValueAsString(value);
  }

  private static Optional<UUID> parse(String id) {
    try {
      return Optional.of(UUID.fromString(id));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
