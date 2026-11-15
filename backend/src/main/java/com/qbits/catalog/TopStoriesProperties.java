package com.qbits.catalog;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Top stories slider (spec 009).
 *
 * @param enabled false makes the endpoint return an empty list
 * @param minCoverage fewest distinct sources a story needs to qualify (R2.2)
 * @param limit most stories returned (R2.1)
 */
@Validated
@ConfigurationProperties(prefix = "qbits.top-stories")
public record TopStoriesProperties(
    boolean enabled, @Min(1) int minCoverage, @Min(1) @Max(10) int limit) {}
