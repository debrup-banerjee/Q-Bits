package com.qbits.images;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Settings for story pictures (spec 009). */
@Validated
@ConfigurationProperties(prefix = "qbits.images")
public record ImagesProperties(
    @NotNull Duration tick,
    @Min(1) int batchSize,
    @NotNull Duration reuseWindow,
    @NotEmpty List<Duration> retryDelays,
    @NotNull Pexels pexels,
    @NotNull Query query) {

  /**
   * Pexels API access (spec 009 R2). Without an API key no photo is searched and stories keep their
   * cover art.
   */
  public record Pexels(
      String apiKey,
      @NotBlank String baseUrl,
      @Min(1) int perHour,
      @NotNull Duration minSpacing,
      @NotNull Duration timeout,
      @NotBlank String userAgent) {

    public boolean configured() {
      return apiKey != null && !apiKey.isBlank();
    }
  }

  /** The model call that turns our own headline and summary into a photo search phrase. */
  public record Query(
      String model,
      @NotBlank String promptVersion,
      @Min(1) int maxOutputTokens,
      @NotNull Duration timeout,
      @Min(0) int dailyCallCap) {

    public boolean modelConfigured() {
      return model != null && !model.isBlank();
    }
  }
}
