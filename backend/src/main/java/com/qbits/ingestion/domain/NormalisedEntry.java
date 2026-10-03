package com.qbits.ingestion.domain;

import java.time.Instant;

/** A cleaned feed entry ready for the relevance pre-filter and de-duplication. */
public record NormalisedEntry(
    String title,
    String excerpt,
    String canonicalUrl,
    String titleKey,
    Instant publishedAt,
    boolean dateEstimated) {}
