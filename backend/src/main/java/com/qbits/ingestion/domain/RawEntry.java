package com.qbits.ingestion.domain;

import java.time.Instant;

/** One entry as read from a feed, before any cleanup. Any field may be null. */
public record RawEntry(
    String title, String link, String descriptionHtml, Instant publishedAt, Instant updatedAt) {}
