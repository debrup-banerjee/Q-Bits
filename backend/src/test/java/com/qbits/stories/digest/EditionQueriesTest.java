package com.qbits.stories.digest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.qbits.stories.StoryFixtures;
import com.qbits.stories.StoryWriterProperties.Mode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EditionQueriesTest {

  private static final Instant TODAY_CUT = Instant.parse("2026-10-04T00:30:00Z"); // 06:00 IST
  private static final DigestProperties PROPS =
      new DigestProperties(
          LocalTime.of(6, 0),
          ZoneId.of("Asia/Kolkata"),
          Duration.ofMinutes(5),
          Duration.ofMinutes(10),
          Duration.ofMinutes(30),
          Duration.ofHours(6),
          Duration.ofHours(2));

  private EditionQueries at(Instant now) {
    return new EditionQueries(
        mock(EditionRepository.class),
        PROPS,
        StoryFixtures.props(),
        Clock.fixed(now, ZoneOffset.UTC));
  }

  private static Edition edition(Instant cutoff) {
    return new Edition(
        UUID.randomUUID(),
        cutoff,
        EditionStatus.PUBLISHED,
        null,
        null,
        0,
        0,
        0,
        0,
        cutoff,
        null,
        null,
        cutoff);
  }

  @Test
  void lateOnlyAfterTwoHoursWithoutTodaysEdition() { // 006 R5.5
    Edition yesterday = edition(TODAY_CUT.minus(Duration.ofDays(1)));

    assertThat(at(TODAY_CUT.plus(Duration.ofMinutes(119))).isLate(yesterday)).isFalse();
    assertThat(at(TODAY_CUT.plus(Duration.ofMinutes(121))).isLate(yesterday)).isTrue();
    assertThat(at(TODAY_CUT.plus(Duration.ofHours(5))).isLate(edition(TODAY_CUT))).isFalse();
  }

  @Test
  void realtimeModeMeasuresWindowsFromNowAndIsNeverLate() { // 006 R6.4, 006 R4
    Instant now = TODAY_CUT.plus(Duration.ofHours(30));
    Edition old = published(TODAY_CUT.minus(Duration.ofDays(1)));
    EditionRepository repo = mock(EditionRepository.class);
    when(repo.findLatestPublished()).thenReturn(Optional.of(old));

    EditionQueries realtime =
        new EditionQueries(
            repo, PROPS, StoryFixtures.props(Mode.REALTIME), Clock.fixed(now, ZoneOffset.UTC));
    EditionQueries digest =
        new EditionQueries(
            repo, PROPS, StoryFixtures.props(Mode.DIGEST), Clock.fixed(now, ZoneOffset.UTC));

    assertThat(realtime.windowEnd()).isEqualTo(now);
    assertThat(realtime.dataAsOf()).isEmpty(); // the newest story's time is used instead
    assertThat(realtime.isLate(old)).isFalse();
    assertThat(digest.windowEnd()).isEqualTo(old.cutoffAt());
    assertThat(digest.dataAsOf()).contains(old.publishedAt());
    assertThat(digest.isLate(old)).isTrue();
  }

  @Test
  void healthWarnsWhenTheLatestEditionFailedOrIsLate() { // 006 R6.5
    Instant now = TODAY_CUT.plus(Duration.ofHours(3));
    Edition yesterday = published(TODAY_CUT.minus(Duration.ofDays(1)));
    EditionRepository repo = mock(EditionRepository.class);
    when(repo.findLatestPublished()).thenReturn(Optional.of(yesterday));
    when(repo.findLatest()).thenReturn(Optional.of(yesterday));
    EditionQueries queries =
        new EditionQueries(repo, PROPS, StoryFixtures.props(), Clock.fixed(now, ZoneOffset.UTC));
    EditionHealthIndicator health = new EditionHealthIndicator(repo, queries);

    assertThat(health.health().getStatus().getCode()).isEqualTo("UP");
    assertThat(health.health().getDetails())
        .containsEntry("warning", EditionHealthIndicator.LATE_WARNING);

    Edition failed =
        new Edition(
            UUID.randomUUID(),
            TODAY_CUT,
            EditionStatus.FAILED,
            null,
            null,
            0,
            0,
            0,
            0,
            TODAY_CUT,
            null,
            null,
            null);
    when(repo.findLatest()).thenReturn(Optional.of(failed));
    assertThat(health.health().getStatus().getCode()).isEqualTo("UP");
    assertThat(health.health().getDetails())
        .containsEntry("warning", EditionHealthIndicator.FAILED_WARNING);

    when(repo.findLatest()).thenReturn(Optional.of(published(TODAY_CUT)));
    when(repo.findLatestPublished()).thenReturn(Optional.of(published(TODAY_CUT)));
    assertThat(health.health().getDetails()).doesNotContainKey("warning");
  }

  private static Edition published(Instant cutoff) {
    return new Edition(
        UUID.randomUUID(),
        cutoff,
        EditionStatus.PUBLISHED,
        null,
        null,
        0,
        0,
        0,
        0,
        cutoff,
        null,
        null,
        cutoff.plus(Duration.ofMinutes(40)));
  }
}
