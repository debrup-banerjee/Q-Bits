package com.qbits.ingestion.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Reads an HTTP {@code Retry-After} value (RFC 9110 section 10.2.3): either a number of seconds or
 * an HTTP-date. The result is capped so a bad header can never park a source for long.
 */
public final class RetryAfter {

  /** Longest wait we accept from a publisher. */
  public static final Duration MAX = Duration.ofHours(24);

  private RetryAfter() {}

  /**
   * The time before which the source should not be fetched again, or empty when the value is
   * missing, unreadable or not in the future.
   */
  public static Optional<Instant> until(String header, Instant now) {
    if (header == null || header.isBlank()) {
      return Optional.empty();
    }
    String value = header.trim();
    Instant until;
    if (value.chars().allMatch(c -> c >= '0' && c <= '9')) {
      // Many digits means a very long wait; anything at or past the cap is the cap.
      boolean huge = value.length() > 12;
      long seconds = huge ? Long.MAX_VALUE : Long.parseLong(value);
      until = seconds >= MAX.toSeconds() ? now.plus(MAX) : now.plusSeconds(seconds);
    } else {
      try {
        until = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
      } catch (DateTimeParseException e) {
        return Optional.empty();
      }
    }
    if (!until.isAfter(now)) {
      return Optional.empty();
    }
    Instant cap = now.plus(MAX);
    return Optional.of(until.isAfter(cap) ? cap : until);
  }
}
