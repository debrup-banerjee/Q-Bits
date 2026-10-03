package com.qbits.ingestion.domain;

import java.time.Instant;

/** One row of the per-source fetch log (spec 001 R7.2). */
public record FetchLogEntry(
    String sourceId,
    Instant startedAt,
    long durationMs,
    FetchStatus status,
    Integer httpStatus,
    int itemsNew,
    int itemsDuplicate,
    int itemsNotAi,
    int itemsMalformed,
    int itemsTooOld,
    String error) {}
