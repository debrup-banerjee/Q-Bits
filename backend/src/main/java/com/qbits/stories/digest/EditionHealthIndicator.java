package com.qbits.stories.digest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Edition detail for {@code /actuator/health} under "digest" (spec 006 R6.5): the latest edition's
 * status, cut-off and published time, plus the last published edition when the latest is not yet
 * (or never) published. Readers still get the last edition when something goes wrong, so the app
 * stays UP; a {@code warning} detail says when the latest edition failed or is running late.
 */
@Component("digest")
public class EditionHealthIndicator implements HealthIndicator {

  static final String FAILED_WARNING = "the latest edition failed; readers see the previous one";
  static final String LATE_WARNING = "today's edition is running late; readers see the last one";

  private final EditionRepository editions;
  private final EditionQueries queries;

  public EditionHealthIndicator(EditionRepository editions, EditionQueries queries) {
    this.editions = editions;
    this.queries = queries;
  }

  @Override
  public Health health() {
    Map<String, Object> details = new LinkedHashMap<>();
    Optional<Edition> latest = editions.findLatest();
    latest.ifPresent(
        e -> {
          details.put("status", e.status().name());
          details.put("cutoffAt", e.cutoffAt().toString());
          if (e.publishedAt() != null) {
            details.put("publishedAt", e.publishedAt().toString());
            details.put("stories", e.published());
          }
        });
    Optional<Edition> published = editions.findLatestPublished();
    published
        .filter(p -> !details.containsKey("publishedAt"))
        .ifPresent(
            p -> {
              details.put("lastPublishedCutoffAt", p.cutoffAt().toString());
              details.put("lastPublishedAt", p.publishedAt().toString());
            });
    if (latest.map(e -> e.status() == EditionStatus.FAILED).orElse(false)) {
      details.put("warning", FAILED_WARNING);
    } else if (published.map(queries::isLate).orElse(false)) {
      details.put("warning", LATE_WARNING);
    }
    return Health.up().withDetails(details).build();
  }
}
