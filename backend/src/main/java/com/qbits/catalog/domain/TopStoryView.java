package com.qbits.catalog.domain;

import com.qbits.resources.domain.ResourceLink;
import com.qbits.stories.domain.KeyTerm;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A story in the top stories slider: the usual story fields plus how many different sources
 * reported it (spec 009 R2.3).
 */
public record TopStoryView(
    UUID id,
    StoryView.SectionRef section,
    String headline,
    String summary,
    List<KeyTerm> keyTerms,
    StoryView.SourceRef source,
    String originalUrl,
    Instant publishedAt,
    boolean dateEstimated,
    String attribution,
    List<ResourceLink> resources,
    @Schema(description = "Distinct sources that reported this event, at least 2.") int coverage) {

  public static TopStoryView of(StoryView s, int coverage) {
    return new TopStoryView(
        s.id(),
        s.section(),
        s.headline(),
        s.summary(),
        s.keyTerms(),
        s.source(),
        s.originalUrl(),
        s.publishedAt(),
        s.dateEstimated(),
        s.attribution(),
        s.resources(),
        coverage);
  }
}
