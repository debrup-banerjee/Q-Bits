package com.qbits.pages.api;

import com.qbits.catalog.CatalogService;
import com.qbits.catalog.domain.Cursor;
import com.qbits.catalog.domain.CursorCodec;
import com.qbits.catalog.domain.StoryPage;
import com.qbits.catalog.domain.StoryView;
import com.qbits.pages.PageShell;
import com.qbits.pages.domain.Sitemaps;
import com.qbits.pages.domain.Sitemaps.UrlEntry;
import com.qbits.stories.domain.Section;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code robots.txt} and the sitemaps (spec 007 R6, R7.2). */
@RestController
public class CrawlController {

  /** Enough for every story in the 72-hour window several times over. */
  static final int MAX_PAGES = 50;

  static final int PAGE_SIZE = 100;

  private static final MediaType XML = MediaType.parseMediaType("application/xml;charset=UTF-8");
  private static final MediaType TEXT = MediaType.parseMediaType("text/plain;charset=UTF-8");
  private static final CacheControl HOURLY = CacheControl.maxAge(Duration.ofHours(1)).cachePublic();

  private final CatalogService catalog;
  private final PageShell shell;
  private final Clock clock;

  public CrawlController(CatalogService catalog, PageShell shell, Clock clock) {
    this.catalog = catalog;
    this.shell = shell;
    this.clock = clock;
  }

  @GetMapping("/robots.txt")
  public ResponseEntity<String> robots() {
    return send(TEXT, Sitemaps.robots(siteUrl()));
  }

  @GetMapping("/sitemap.xml")
  public ResponseEntity<String> sitemap() {
    Instant asOf = catalog.dataAsOf().orElse(null);
    List<UrlEntry> entries = new ArrayList<>();
    entries.add(new UrlEntry("/", asOf));
    entries.add(new UrlEntry("/sections", asOf));
    entries.add(new UrlEntry("/about", null));
    for (Section s : Section.values()) {
      entries.add(new UrlEntry("/section/" + s.slug(), asOf));
    }
    for (StoryView s : allStories()) {
      entries.add(new UrlEntry("/story/" + s.id(), s.publishedAt()));
    }
    return send(XML, Sitemaps.sitemap(siteUrl(), entries));
  }

  @GetMapping("/sitemap-news.xml")
  public ResponseEntity<String> newsSitemap() {
    return send(XML, Sitemaps.newsSitemap(siteUrl(), allStories(), clock.instant()));
  }

  /** Every story the stories API returns now, newest first. */
  private List<StoryView> allStories() {
    List<StoryView> all = new ArrayList<>();
    Optional<Cursor> cursor = Optional.empty();
    for (int i = 0; i < MAX_PAGES; i++) {
      StoryPage page = catalog.list(Optional.empty(), cursor, PAGE_SIZE, CatalogService.WINDOW);
      all.addAll(page.data());
      if (page.nextCursor() == null) {
        break;
      }
      cursor = CursorCodec.decode(page.nextCursor());
    }
    return all;
  }

  private String siteUrl() {
    return shell.renderer().siteUrl();
  }

  private static ResponseEntity<String> send(MediaType type, String body) {
    return ResponseEntity.ok().contentType(type).cacheControl(HOURLY).body(body);
  }
}
