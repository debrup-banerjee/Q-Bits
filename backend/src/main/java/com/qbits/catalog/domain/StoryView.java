package com.qbits.catalog.domain;

import com.qbits.images.domain.StoryImage;
import com.qbits.resources.domain.ResourceLink;
import com.qbits.stories.domain.KeyTerm;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A story as clients see it (spec 003 R2.3). Built only from our own story fields plus the source
 * name, homepage and original link. Never carries the publisher's title or excerpt (R2.4). {@code
 * image} is null until a safe picture is found; clients then draw cover art (spec 009 R5).
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
    String attribution,
    List<ResourceLink> resources,
    @Schema(types = {"object", "null"}) StoryImage image) {

  /** A story without a picture. */
  public StoryView(
      UUID id,
      SectionRef section,
      String headline,
      String summary,
      List<KeyTerm> keyTerms,
      SourceRef source,
      String originalUrl,
      Instant publishedAt,
      boolean dateEstimated,
      String attribution,
      List<ResourceLink> resources) {
    this(
        id,
        section,
        headline,
        summary,
        keyTerms,
        source,
        originalUrl,
        publishedAt,
        dateEstimated,
        attribution,
        resources,
        null);
  }

  /** Section slug and display name. */
  public record SectionRef(String slug, String name) {}

  /** Source display name and homepage; homepage is null if the source left the approved list. */
  public record SourceRef(String name, @Schema(types = {"string", "null"}) String homepage) {}

  public static String attributionFor(String sourceName) {
    return "Summary written from " + sourceName + "'s headline and teaser";
  }
}
