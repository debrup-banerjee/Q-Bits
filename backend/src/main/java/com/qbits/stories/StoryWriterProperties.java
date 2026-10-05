package com.qbits.stories;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings for writing stories (spec 002). The API key comes only from ANTHROPIC_API_KEY. {@code
 * mode} chooses the daily digest or one-at-a-time writing (spec 006 R6.4); any other value stops
 * the app at startup.
 */
@Validated
@ConfigurationProperties(prefix = "qbits.story-writer")
public record StoryWriterProperties(
    @NotNull Mode mode,
    @NotBlank String provider,
    String model,
    @NotBlank String promptVersion,
    @Min(100) int maxOutputTokens,
    @Min(1) int dailyCallCap,
    @Min(1) int parallelism,
    @Min(1) int batchSize,
    @NotNull Duration timeout,
    @NotNull Duration tick,
    @NotNull Duration maxBackoff) {

  /** How stories are written (spec 006 R6.4). */
  public enum Mode {
    /** Once a day in one batch; an edition goes live all at once (spec 006). */
    DIGEST,
    /** One at a time as items arrive (spec 002). */
    REALTIME
  }

  public boolean realtime() {
    return mode == Mode.REALTIME;
  }
}
