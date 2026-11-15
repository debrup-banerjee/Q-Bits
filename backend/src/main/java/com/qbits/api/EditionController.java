package com.qbits.api;

import com.qbits.common.AppException;
import com.qbits.stories.digest.Edition;
import com.qbits.stories.digest.EditionQueries;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The latest daily edition (spec 006 R5.1). */
@RestController
@RequestMapping("/api/v1")
public class EditionController {

  /** Latest published edition and when the next one is due. */
  public record EditionView(
      UUID id,
      Instant cutoffAt,
      Instant publishedAt,
      int storyCount,
      Instant nextCutoffAt,
      boolean late) {

    /** The view of an edition; also embedded in server-filled pages (spec 007 R4.1). */
    public static EditionView of(Edition e, EditionQueries editions) {
      return new EditionView(
          e.id(),
          e.cutoffAt(),
          e.publishedAt(),
          e.published(),
          editions.nextCutoff(),
          editions.isLate(e));
    }
  }

  private final EditionQueries editions;

  public EditionController(EditionQueries editions) {
    this.editions = editions;
  }

  @GetMapping("/edition")
  public ResponseEntity<EditionView> edition() {
    Edition e =
        editions
            .latestPublished()
            .orElseThrow(
                () -> new AppException("NO_EDITION", HttpStatus.NOT_FOUND, "No edition yet."));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic())
        .header(CatalogController.DATA_AS_OF, e.publishedAt().toString())
        .body(EditionView.of(e, editions));
  }
}
