package com.qbits.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.qbits.catalog.domain.TopStoryRules.Candidate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TopStoryRulesTest {

  private static final Instant T = Instant.parse("2026-10-03T06:00:00Z");

  private static Candidate c(int idNo, boolean official, long minutes, int coverage) {
    return new Candidate(
        new UUID(0, idNo), "s" + idNo, official, T.plusSeconds(minutes * 60), coverage);
  }

  @Test
  void dropsCoverageBelowTheMinimum() { // 009 R2.2
    List<Candidate> picked = TopStoryRules.pick(List.of(c(1, true, 0, 1), c(2, false, 0, 2)), 2, 3);

    assertThat(picked).extracting(Candidate::coverage).containsExactly(2);
  }

  @Test
  void ordersByCoverageThenOfficialThenEarliestThenId() { // 009 R2.1
    Candidate most = c(1, false, 50, 5);
    Candidate officialTie = c(2, true, 40, 3);
    Candidate earlierTie = c(3, false, 10, 3);
    Candidate laterTie = c(4, false, 20, 3);
    Candidate sameTimeLowId = c(5, false, 10, 3);

    List<Candidate> picked =
        TopStoryRules.pick(List.of(laterTie, sameTimeLowId, earlierTie, officialTie, most), 2, 10);

    assertThat(picked).containsExactly(most, officialTie, earlierTie, sameTimeLowId, laterTie);
  }

  @Test
  void returnsAtMostLimitAndFewerWhenFewQualify() { // 009 R2.1, R2.2
    List<Candidate> four =
        List.of(c(1, false, 1, 4), c(2, false, 2, 3), c(3, false, 3, 2), c(4, false, 4, 2));

    assertThat(TopStoryRules.pick(four, 2, 3)).hasSize(3);
    assertThat(TopStoryRules.pick(four, 3, 3)).hasSize(2);
    assertThat(TopStoryRules.pick(List.of(), 2, 3)).isEmpty();
  }

  @Test
  void sameInputGivesSameOutput() { // 009 R1.3
    List<Candidate> in = List.of(c(1, false, 1, 3), c(2, true, 2, 3));

    assertThat(TopStoryRules.pick(in, 2, 3)).isEqualTo(TopStoryRules.pick(in, 2, 3));
  }
}
