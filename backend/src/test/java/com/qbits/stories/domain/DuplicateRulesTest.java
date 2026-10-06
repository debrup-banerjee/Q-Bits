package com.qbits.stories.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DuplicateRulesTest {

  private static final Instant T = Instant.parse("2026-10-05T15:00:00Z");

  private static GroupCandidate c(
      int n, String source, boolean official, int minutesAfter, boolean published) {
    return new GroupCandidate(
        id(n),
        "Headline " + n,
        "Summary " + n,
        Section.GLOBAL_AI_TECH,
        source,
        source,
        official,
        T.plusSeconds(60L * minutesAfter),
        published);
  }

  private static UUID id(int n) {
    return new UUID(0, n);
  }

  // The 2026-10-05 watermark trio: OpenAI's own post, The Verge, TechCrunch.
  private static final GroupCandidate OPENAI = c(1, "openai-news", true, 0, false);
  private static final GroupCandidate VERGE = c(2, "the-verge-ai", false, 188, false);
  private static final GroupCandidate TECHCRUNCH = c(3, "techcrunch-ai", false, 336, false);

  @Test
  void keepsTheOfficialSourceAndHoldsBackThePress() { // 008 R2.1, R2.3
    var r =
        DuplicateRules.resolve(
            List.of(OPENAI, VERGE, TECHCRUNCH), List.of(Set.of(id(1), id(2), id(3))), true);

    assertThat(r.heldBack()).isEqualTo(Map.of(id(2), id(1), id(3), id(1)));
    assertThat(r.groups()).isEqualTo(1);
    assertThat(r.dropped()).isZero();
  }

  @Test
  void prefersOfficialEvenWhenItIsLater() { // 008 R2.3
    GroupCandidate earlyPress = c(4, "the-verge-ai", false, 0, false);
    GroupCandidate laterOfficial = c(5, "openai-news", true, 120, false);

    var r =
        DuplicateRules.resolve(
            List.of(earlyPress, laterOfficial), List.of(Set.of(id(4), id(5))), true);

    assertThat(r.heldBack()).isEqualTo(Map.of(id(4), id(5)));
  }

  @Test
  void keepsTheEarliestAmongEquals() { // 008 R2.3
    var r = DuplicateRules.resolve(List.of(VERGE, TECHCRUNCH), List.of(Set.of(id(2), id(3))), true);

    assertThat(r.heldBack()).isEqualTo(Map.of(id(3), id(2)));
  }

  @Test
  void breaksATieOnTheSourceId() { // 008 R2.3
    GroupCandidate b = c(6, "b-source", false, 10, false);
    GroupCandidate a = c(7, "a-source", false, 10, false);

    var r = DuplicateRules.resolve(List.of(b, a), List.of(Set.of(id(6), id(7))), true);

    assertThat(r.heldBack()).isEqualTo(Map.of(id(6), id(7)));
  }

  @Test
  void aLiveStoryIsKeptOverABetterNewOne() { // 008 R2.2
    GroupCandidate livePress = c(8, "the-verge-ai", false, 300, true);

    var r = DuplicateRules.resolve(List.of(OPENAI, livePress), List.of(Set.of(id(1), id(8))), true);

    assertThat(r.heldBack()).isEqualTo(Map.of(id(1), id(8)));
  }

  @Test
  void anEditionNeverWithdrawsALiveStory() { // 008 R2.2
    GroupCandidate live1 = c(9, "the-verge-ai", false, 0, true);
    GroupCandidate live2 = c(10, "techcrunch-ai", false, 30, true);
    GroupCandidate fresh = c(11, "wired-ai", false, 60, false);

    var r =
        DuplicateRules.resolve(
            List.of(live1, live2, fresh), List.of(Set.of(id(9), id(10), id(11))), true);

    assertThat(r.heldBack()).isEqualTo(Map.of(id(11), id(9)));
  }

  @Test
  void theCleanUpMayWithdrawLiveCopies() { // 008 R4.1
    GroupCandidate liveOfficial = c(12, "openai-news", true, 0, true);
    GroupCandidate livePress = c(13, "the-verge-ai", false, 100, true);

    var r =
        DuplicateRules.resolve(
            List.of(liveOfficial, livePress), List.of(Set.of(id(12), id(13))), false);

    assertThat(r.heldBack()).isEqualTo(Map.of(id(13), id(12)));
  }

  @Test
  void dropsGroupsWithUnknownIdsOverlapsOrOneMember() { // 008 R1.4
    var r =
        DuplicateRules.resolve(
            List.of(OPENAI, VERGE, TECHCRUNCH),
            List.of(
                Set.of(id(1), id(99)), // unknown story
                Set.of(id(2)), // one member
                Set.of(id(1), id(3)), // id 3 also below
                Set.of(id(2), id(3))),
            true);

    assertThat(r.heldBack()).isEmpty();
    assertThat(r.groups()).isZero();
    assertThat(r.dropped()).isEqualTo(4);
  }

  @Test
  void keepsValidGroupsBesideInvalidOnes() { // 008 R1.4
    GroupCandidate x = c(20, "bbc-technology", false, 0, false);
    GroupCandidate y = c(21, "guardian-ai", false, 5, false);

    var r =
        DuplicateRules.resolve(
            List.of(OPENAI, VERGE, x, y),
            List.of(Set.of(id(1), id(2)), Set.of(id(20), id(99)), Set.of(id(21))),
            true);

    assertThat(r.heldBack()).isEqualTo(Map.of(id(2), id(1)));
    assertThat(r.groups()).isEqualTo(1);
    assertThat(r.dropped()).isEqualTo(2);
  }

  @Test
  void theSameInputGivesTheSameAnswer() { // 008 R3.3
    List<GroupCandidate> cands = List.of(TECHCRUNCH, OPENAI, VERGE);
    List<Set<UUID>> groups = List.of(Set.of(id(3), id(2), id(1)));

    assertThat(DuplicateRules.resolve(cands, groups, true))
        .isEqualTo(DuplicateRules.resolve(List.of(VERGE, TECHCRUNCH, OPENAI), groups, true));
  }
}
