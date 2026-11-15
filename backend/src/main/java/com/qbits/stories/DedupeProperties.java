package com.qbits.stories;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * One story per event (spec 008).
 *
 * @param enabled false publishes editions without grouping, as before this spec
 * @param promptVersion selects {@code prompts/story-grouper.<version>.md}
 * @param timeout how long one grouping call may take
 * @param maxOutputTokens cap on the grouping answer
 */
@Validated
@ConfigurationProperties(prefix = "qbits.dedupe")
public record DedupeProperties(
    boolean enabled,
    @NotBlank String promptVersion,
    @NotNull Duration timeout,
    @Min(100) int maxOutputTokens) {}
