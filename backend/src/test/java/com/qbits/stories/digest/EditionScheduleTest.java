package com.qbits.stories.digest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class EditionScheduleTest {

  // 06:00 IST = 00:30 UTC
  private final EditionSchedule schedule =
      new EditionSchedule(LocalTime.of(6, 0), ZoneId.of("Asia/Kolkata"));

  @Test
  void latestCutoffBeforeAndAfterSixAmIst() { // 006 R1.1
    assertThat(schedule.latestCutoffAtOrBefore(Instant.parse("2026-10-04T00:29:59Z")))
        .isEqualTo(Instant.parse("2026-10-03T00:30:00Z"));
    assertThat(schedule.latestCutoffAtOrBefore(Instant.parse("2026-10-04T00:30:00Z")))
        .isEqualTo(Instant.parse("2026-10-04T00:30:00Z"));
    assertThat(schedule.latestCutoffAtOrBefore(Instant.parse("2026-10-04T23:59:00Z")))
        .isEqualTo(Instant.parse("2026-10-04T00:30:00Z"));
  }

  @Test
  void nextCutoffAcrossUtcMidnight() {
    assertThat(schedule.nextCutoffAfter(Instant.parse("2026-10-03T22:00:00Z")))
        .isEqualTo(Instant.parse("2026-10-04T00:30:00Z"));
    assertThat(schedule.nextCutoffAfter(Instant.parse("2026-10-04T00:30:00Z")))
        .isEqualTo(Instant.parse("2026-10-05T00:30:00Z"));
  }

  @Test
  void windowsEndAtTheCutoff() { // 006 R4.2
    Instant cutoff = Instant.parse("2026-10-04T00:30:00Z");
    assertThat(EditionSchedule.windowStart(cutoff, Duration.ofHours(24)))
        .isEqualTo(Instant.parse("2026-10-03T00:30:00Z"));
  }
}
