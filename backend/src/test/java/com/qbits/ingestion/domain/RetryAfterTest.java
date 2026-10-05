package com.qbits.ingestion.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RetryAfterTest {

  private static final Instant NOW = Instant.parse("2026-10-03T06:00:00Z");

  @Test
  void readsSeconds() { // 001 R7.1
    assertThat(RetryAfter.until("7200", NOW)).contains(NOW.plusSeconds(7200));
    assertThat(RetryAfter.until(" 120 ", NOW)).contains(NOW.plusSeconds(120));
  }

  @Test
  void readsHttpDate() { // 001 R7.1
    assertThat(RetryAfter.until("Sat, 03 Oct 2026 09:30:00 GMT", NOW))
        .contains(Instant.parse("2026-10-03T09:30:00Z"));
  }

  @Test
  void capsAtTwentyFourHours() { // 001 R7.1
    Instant cap = NOW.plus(Duration.ofHours(24));

    assertThat(RetryAfter.until("604800", NOW)).contains(cap);
    assertThat(RetryAfter.until("99999999999999999999999", NOW)).contains(cap);
    assertThat(RetryAfter.until("Fri, 09 Oct 2026 06:00:00 GMT", NOW)).contains(cap);
  }

  @Test
  void ignoresMissingUnreadableOrPastValues() { // 001 R7.1
    assertThat(RetryAfter.until(null, NOW)).isEmpty();
    assertThat(RetryAfter.until("", NOW)).isEmpty();
    assertThat(RetryAfter.until("soon", NOW)).isEmpty();
    assertThat(RetryAfter.until("-5", NOW)).isEmpty();
    assertThat(RetryAfter.until("0", NOW)).isEmpty();
    assertThat(RetryAfter.until("Fri, 02 Oct 2026 06:00:00 GMT", NOW)).isEmpty();
  }
}
