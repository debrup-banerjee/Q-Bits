package com.qbits.resources;

import com.qbits.resources.domain.ResourceHost;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Settings for open-source link checks (spec 005). */
@Validated
@ConfigurationProperties(prefix = "qbits.resources")
public record ResourcesProperties(
    @NotNull Duration tick,
    @Min(1) int batchSize,
    @Min(1) int maxPerStory,
    @NotNull Duration cacheTtl,
    @NotEmpty List<Duration> retryDelays,
    String githubToken,
    @NotEmpty Map<ResourceHost, String> baseUrls,
    @NotEmpty Map<ResourceHost, RateLimit> rateLimits) {

  /** At most {@code perHour} calls an hour, and at least {@code minSpacing} between calls. */
  public record RateLimit(@Min(1) int perHour, @NotNull Duration minSpacing) {}
}
