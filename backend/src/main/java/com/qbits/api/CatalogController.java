package com.qbits.api;

import com.qbits.api.ApiModels.SectionView;
import com.qbits.api.ApiModels.SiteView;
import com.qbits.api.ApiModels.SourceView;
import com.qbits.catalog.CatalogService;
import com.qbits.catalog.domain.Cursor;
import com.qbits.catalog.domain.CursorCodec;
import com.qbits.catalog.domain.StoryPage;
import com.qbits.catalog.domain.StoryView;
import com.qbits.common.AppException;
import com.qbits.config.QBitsProperties;
import com.qbits.stories.domain.Section;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only API for web and mobile clients (spec 003 R1–R3, R7.3). */
@RestController
@RequestMapping("/api/v1")
public class CatalogController {

  static final String DATA_AS_OF = "X-Data-As-Of";
  private static final int DEFAULT_LIMIT = 20;
  private static final int MAX_LIMIT = 100;

  private final CatalogService catalog;
  private final QBitsProperties props;

  public CatalogController(CatalogService catalog, QBitsProperties props) {
    this.catalog = catalog;
    this.props = props;
  }

  @GetMapping("/sections")
  public ResponseEntity<List<SectionView>> sections() {
    Map<Section, Long> counts = catalog.counts();
    List<SectionView> body =
        Arrays.stream(Section.values())
            .map(s -> new SectionView(s.slug(), s.displayName(), s.description(), counts.get(s)))
            .toList();
    return ok(body);
  }

  @GetMapping("/stories")
  public ResponseEntity<StoryPage> stories(
      @RequestParam(required = false) String section,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    Optional<Section> s =
        section == null || section.isBlank()
            ? Optional.empty()
            : Optional.of(
                Section.fromSlug(section)
                    .orElseThrow(
                        () ->
                            new AppException(
                                "UNKNOWN_SECTION",
                                HttpStatus.BAD_REQUEST,
                                "Unknown section '" + section + "'.")));
    Optional<Cursor> c =
        cursor == null || cursor.isBlank()
            ? Optional.empty()
            : Optional.of(
                CursorCodec.decode(cursor)
                    .orElseThrow(
                        () ->
                            new AppException(
                                "INVALID_CURSOR", HttpStatus.BAD_REQUEST, "Invalid cursor.")));
    int size = limit == null ? DEFAULT_LIMIT : limit;
    if (size < 1 || size > MAX_LIMIT) {
      throw new AppException(
          "INVALID_LIMIT",
          HttpStatus.BAD_REQUEST,
          "limit must be between 1 and " + MAX_LIMIT + ".");
    }
    return ok(catalog.list(s, c, size));
  }

  @GetMapping("/stories/{id}")
  public ResponseEntity<StoryView> story(@PathVariable UUID id) {
    return ok(
        catalog
            .find(id)
            .orElseThrow(
                () -> new AppException("STORY_NOT_FOUND", HttpStatus.NOT_FOUND, "No such story.")));
  }

  @GetMapping("/sources")
  public ResponseEntity<List<SourceView>> sources() {
    return ok(
        catalog.enabledSources().stream()
            .map(s -> new SourceView(s.name(), s.homepage().toString()))
            .toList());
  }

  @GetMapping("/site")
  public ResponseEntity<SiteView> site() {
    return ok(new SiteView("Q-Bits", props.contactEmail()));
  }

  private <T> ResponseEntity<T> ok(T body) {
    ResponseEntity.BodyBuilder builder =
        ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic());
    catalog.dataAsOf().ifPresent(at -> builder.header(DATA_AS_OF, at.toString()));
    return builder.body(body);
  }
}
