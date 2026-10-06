package com.qbits.sources.domain;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

/**
 * One approved news source from {@code config/sources.yml}. {@code official} marks a company's,
 * lab's, university's or government's own channel (spec 008 R2.6).
 */
public record Source(
    String id,
    String name,
    URI homepage,
    URI feedUrl,
    SourceType type,
    URI termsUrl,
    LocalDate termsReviewedOn,
    boolean enabled,
    boolean aiNative,
    Region region,
    String sectionHint,
    Duration interval,
    boolean official) {

  public Optional<URI> terms() {
    return Optional.ofNullable(termsUrl);
  }

  public Optional<String> hint() {
    return Optional.ofNullable(sectionHint);
  }

  /** Per-source fetch interval, if the entry overrides the default. */
  public Optional<Duration> intervalOverride() {
    return Optional.ofNullable(interval);
  }
}
