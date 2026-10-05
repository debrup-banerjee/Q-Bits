package com.qbits.stories.digest;

import java.time.Instant;
import java.util.UUID;

/** One daily edition and its batch progress. */
public record Edition(
    UUID id,
    Instant cutoffAt,
    EditionStatus status,
    String batchId,
    String retryBatchId,
    int submitted,
    int published,
    int notAi,
    int rejected,
    Instant createdAt,
    Instant lastPolledAt,
    Instant nextSubmitAt,
    Instant publishedAt) {}
