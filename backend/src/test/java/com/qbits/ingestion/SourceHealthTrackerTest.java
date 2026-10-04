package com.qbits.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.qbits.ingestion.domain.FetchStatus;
import com.qbits.ingestion.domain.SourceHealth;
import com.qbits.ingestion.domain.SourceState;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SourceHealthTrackerTest {

  private static final Instant NOW = Instant.parse("2026-10-03T06:00:00Z");

  private final SourceHealthTracker tracker =
      new SourceHealthTracker(
          new IngestionProperties(
              Duration.ofMinutes(5),
              Duration.ofMinutes(30),
              Duration.ofMinutes(15),
              Duration.ofSeconds(10),
              2,
              Duration.ofSeconds(2),
              Duration.ofHours(72),
              Duration.ofDays(7),
              "0 15 2 * * *",
              Duration.ofHours(24),
              5,
              "QBits/test",
              "QBits"));

  @Test
  void becomesDegradedAfterFiveFailuresInARow() { // 001 R7.4
    SourceState state = SourceState.initial("src");
    for (int i = 1; i <= 4; i++) {
      state = tracker.next(state, FetchStatus.FAILED, NOW);
      assertThat(state.health()).isEqualTo(SourceHealth.OK);
    }

    state = tracker.next(state, FetchStatus.FAILED, NOW);

    assertThat(state.health()).isEqualTo(SourceHealth.DEGRADED);
    assertThat(state.consecutiveFailures()).isEqualTo(5);
  }

  @Test
  void oneSuccessClearsDegraded() { // 001 R7.4
    SourceState degraded =
        new SourceState("src", null, null, NOW, null, 7, SourceHealth.DEGRADED, null);

    SourceState next = tracker.next(degraded, FetchStatus.NOT_MODIFIED, NOW);

    assertThat(next.health()).isEqualTo(SourceHealth.OK);
    assertThat(next.consecutiveFailures()).isZero();
    assertThat(next.lastSuccessAt()).isEqualTo(NOW);
  }

  @Test
  void unavailableRobotsTxtCountsTowardDegraded() { // 001 R7.4, R3.4
    SourceState state = SourceState.initial("src");
    for (int i = 1; i <= 4; i++) {
      state = tracker.next(state, FetchStatus.ROBOTS_UNAVAILABLE, NOW);
    }
    assertThat(state.health()).isEqualTo(SourceHealth.OK);

    state = tracker.next(state, FetchStatus.FAILED, NOW);

    assertThat(state.health()).isEqualTo(SourceHealth.DEGRADED);
    assertThat(state.consecutiveFailures()).isEqualTo(5);
  }

  @Test
  void keepsRetryAfterSetByTheCaller() { // 001 R7.1
    SourceState state = SourceState.initial("src").withRetryAfter(NOW.plusSeconds(60));

    assertThat(tracker.next(state, FetchStatus.FAILED, NOW).retryAfter())
        .isEqualTo(NOW.plusSeconds(60));
  }

  @Test
  void blockedByRobotsCountsNeitherAsFailureNorSuccess() { // 001 R3.2
    SourceState state = new SourceState("src", null, null, null, null, 2, SourceHealth.OK, null);

    SourceState next = tracker.next(state, FetchStatus.BLOCKED_BY_ROBOTS, NOW);

    assertThat(next.consecutiveFailures()).isEqualTo(2);
    assertThat(next.lastSuccessAt()).isNull();
    assertThat(next.lastFetchedAt()).isEqualTo(NOW);
  }
}
