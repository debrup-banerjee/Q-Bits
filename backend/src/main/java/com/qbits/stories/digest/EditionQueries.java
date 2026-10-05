package com.qbits.stories.digest;

import com.qbits.stories.StoryWriterProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Read side of editions for the catalog and API (spec 006 R4, R5). In realtime mode (R6.4) stories
 * go live one at a time, as in specs 002 and 003, so windows end "now" and nothing is late.
 */
@Service
public class EditionQueries {

  private final EditionRepository editions;
  private final Clock clock;
  private final EditionSchedule schedule;
  private final DigestProperties props;
  private final boolean realtime;

  public EditionQueries(
      EditionRepository editions,
      DigestProperties props,
      StoryWriterProperties writer,
      Clock clock) {
    this.editions = editions;
    this.clock = clock;
    this.schedule = new EditionSchedule(props.cutoffTime(), props.zone());
    this.props = props;
    this.realtime = writer.realtime();
  }

  public Optional<Edition> latestPublished() {
    return editions.findLatestPublished();
  }

  /**
   * The instant windows end at: the latest published edition's cut-off, or now before any edition
   * exists (spec 006 R4.1, R4.3) and in realtime mode (R6.4).
   */
  public Instant windowEnd() {
    if (realtime) {
      return clock.instant();
    }
    return latestPublished().map(Edition::cutoffAt).orElseGet(clock::instant);
  }

  /**
   * The latest edition's publish time for {@code X-Data-As-Of} (spec 006 R5.2). Empty before the
   * first edition and in realtime mode, where the newest story's time is used instead.
   */
  public Optional<Instant> dataAsOf() {
    return realtime ? Optional.empty() : latestPublished().map(Edition::publishedAt);
  }

  /**
   * True when the edition for the latest cut-off is missing for longer than the late-notice delay
   * (spec 006 R5.5). Never in realtime mode, where no edition is expected.
   */
  public boolean isLate(Edition latest) {
    if (realtime) {
      return false;
    }
    Instant now = clock.instant();
    Instant expected = schedule.latestCutoffAtOrBefore(now);
    return latest.cutoffAt().isBefore(expected)
        && now.isAfter(expected.plus(props.lateNoticeAfter()));
  }

  public Instant nextCutoff() {
    return schedule.nextCutoffAfter(clock.instant());
  }
}
