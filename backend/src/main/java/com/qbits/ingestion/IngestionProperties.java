package com.qbits.ingestion;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Settings for feed ingestion (spec 001). */
@Validated
@ConfigurationProperties(prefix = "qbits.ingestion")
public record IngestionProperties(
    @NotNull Duration tick,
    @NotNull Duration defaultInterval,
    @NotNull Duration minInterval,
    @NotNull Duration timeout,
    @Min(0) int retries,
    @NotNull Duration retryBackoff,
    @NotNull Duration maxAge,
    @NotNull Duration retention,
    @NotBlank String retentionCron,
    @NotNull Duration robotsCacheTtl,
    @Min(1) int degradedAfterFailures,
    @NotBlank String userAgent,
    @NotBlank String robotsAgentName) {}
