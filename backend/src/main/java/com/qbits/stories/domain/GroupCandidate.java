package com.qbits.stories.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A story offered for same-event grouping (spec 008 R1.3). Only our own headline and summary, never
 * the publisher's title or excerpt.
 *
 * @param official the source is the organisation's own channel (R2.6)
 * @param published already live; false for a story waiting in the edition being published
 */
public record GroupCandidate(
    UUID id,
    String headline,
    String summary,
    Section section,
    String sourceId,
    String sourceName,
    boolean official,
    Instant publishedAt,
    boolean published) {}
