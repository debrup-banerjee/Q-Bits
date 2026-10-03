package com.qbits.stories;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Settings for writing stories (spec 002). The API key comes only from ANTHROPIC_API_KEY. */
@Validated
@ConfigurationProperties(prefix = "qbits.story-writer")
public record StoryWriterProperties(
    @NotBlank String provider,
    String model,
    @NotBlank String promptVersion,
    @Min(100) int maxOutputTokens,
    @Min(1) int dailyCallCap,
    @Min(1) int parallelism,
    @Min(1) int batchSize,
    @NotNull Duration timeout,
    @NotNull Duration tick,
    @NotNull Duration maxBackoff) {}
