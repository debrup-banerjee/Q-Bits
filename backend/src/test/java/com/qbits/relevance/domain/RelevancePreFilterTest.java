package com.qbits.relevance.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RelevancePreFilterTest {

  private final RelevancePreFilter filter =
      new RelevancePreFilter(
          List.of(
              new Keyword("AI", 0.6),
              new Keyword("artificial intelligence", 1.0),
              new Keyword("chips", 0.3),
              new Keyword("GPU", 0.5)),
          1.0);

  @Test
  void keepsEverythingFromAiNativeSourcesWithFullScore() { // 001 R5.1
    assertThat(filter.evaluate("Cricket scores", null, true))
        .isEqualTo(new RelevancePreFilter.Result(1.0, true));
  }

  @ParameterizedTest // 001 R5.3
  @CsvSource({
    "'New AI chips unveiled', true",
    "'AI-powered tools', true",
    "'The firm’s AI: a review', true",
    "'He said the plan was fine', false",
    "'Thailand tourism grows', false",
    "'Waist-high water in Kerala', false",
  })
  void matchesAiOnlyAsAWholeWord(String title, boolean matches) {
    double score = filter.evaluate(title, null, false).score();
    assertThat(score > 0).isEqualTo(matches);
  }

  @Test
  void matchesCaseInsensitively() { // 001 R5.3
    assertThat(filter.evaluate("ARTIFICIAL INTELLIGENCE rules", null, false).keep()).isTrue();
  }

  @Test
  void addsWeightsFromTitleAndExcerptUntilThreshold() { // 001 R5.2, R5.4
    assertThat(filter.evaluate("New AI tool", null, false).keep()).isFalse(); // 0.6
    assertThat(filter.evaluate("New AI tool", "Runs on a GPU", false).keep()).isTrue(); // 1.1
  }

  @Test
  void countsEachKeywordOnce() {
    assertThat(filter.evaluate("AI AI AI AI", null, false).keep()).isFalse();
  }

  @Test
  void capsStoredScoreAtOne() {
    assertThat(filter.evaluate("Artificial intelligence and AI chips on GPU", null, false).score())
        .isEqualTo(1.0);
  }
}
