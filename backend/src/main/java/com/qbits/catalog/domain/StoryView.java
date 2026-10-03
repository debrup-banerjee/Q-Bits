package com.qbits.catalog.domain;

import com.qbits.stories.domain.KeyTerm;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A story as clients see it (spec 003 R2.3). Built only from our own story fields plus the source
 * name, homepage and original link. Never carries the publisher's title or excerpt (R2.4).
 */
public record StoryView(
    UUID id,
    SectionRef section,
    String headline,
    String summary,
    List<KeyTerm> keyTerms,
    SourceRef source,
    String originalUrl,
    Instant publishedAt,
    boolean dateEstimated,
    String attribution) {

  /** Section slug and display name. */
  public record SectionRef(String slug, String name) {}

  /** Source display name and homepage. */
  public record SourceRef(String name, String homepage) {}

  public static String attributionFor(String sourceName) {
    return "Summary written from " + sourceName + "'s headline and teaser";
  }
}
