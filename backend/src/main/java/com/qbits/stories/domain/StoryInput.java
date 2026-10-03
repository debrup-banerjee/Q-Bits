package com.qbits.stories.domain;

import java.time.Instant;

/**
 * Exactly what may be sent to the summary service: the publisher's title and feed excerpt plus
 * source context. Nothing else (spec 002 R1.2; principles).
 */
public record StoryInput(
    String title,
    String excerpt,
    String sourceName,
    String region,
    String sectionHint,
    Instant publishedAt) {}
