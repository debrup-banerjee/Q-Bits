package com.qbits.ingestion.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata for one feed entry. These are the only fields stored from a publisher (spec 001 R4.1).
 */
public record Item(
    UUID id,
    String sourceId,
    String canonicalUrl,
    String sourceTitle,
    String sourceExcerpt,
    String titleKey,
    Instant publishedAt,
    boolean dateEstimated,
    Instant fetchedAt,
    double prefilterScore,
    StoryStatus storyStatus,
    boolean hidden) {}
