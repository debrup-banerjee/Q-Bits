package com.qbits.stories.digest;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** When editions are cut and which window each covers (spec 006 R1.1, R4). Pure. */
public final class EditionSchedule {

  public static final Duration LATEST_WINDOW = Duration.ofHours(24);
  public static final Duration MAX_WINDOW = Duration.ofHours(72);

  private final LocalTime cutoffTime;
  private final ZoneId zone;

  public EditionSchedule(LocalTime cutoffTime, ZoneId zone) {
    this.cutoffTime = cutoffTime;
    this.zone = zone;
  }

  /** The most recent cut-off at or before {@code now}. */
  public Instant latestCutoffAtOrBefore(Instant now) {
    ZonedDateTime local = now.atZone(zone);
    ZonedDateTime today = local.toLocalDate().atTime(cutoffTime).atZone(zone);
    return (today.toInstant().isAfter(now) ? today.minusDays(1) : today).toInstant();
  }

  /** The first cut-off strictly after {@code now}. */
  public Instant nextCutoffAfter(Instant now) {
    return latestCutoffAtOrBefore(now).atZone(zone).plusDays(1).toInstant();
  }

  /** Start of the window of {@code hours} hours ending at the cut-off. */
  public static Instant windowStart(Instant cutoff, Duration window) {
    return cutoff.minus(window);
  }
}
