package com.qbits.relevance;

import static org.assertj.core.api.Assertions.assertThat;

import com.qbits.IntegrationTest;
import com.qbits.relevance.domain.RelevancePreFilter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

/** The keyword list in application.yml, as the app runs it (spec 001 R5). */
class ConfiguredKeywordsIT extends IntegrationTest {

  @Autowired RelevancePreFilter filter;

  @ParameterizedTest // owner request 2026-10-07: AI company business news gets through
  @CsvSource(
      delimiter = '|',
      value = {
        "Sarvam AI appoints ex-Google Cloud India MD Sashikumar Sreedharan as President | true",
        "Krutrim raises fresh funding from new investors | true",
        "Perplexity signs a distribution deal with an Indian telecom firm | true",
        "Startups race to build AI agents for small businesses | true",
        "Thailand tourism grows in the festive season | false",
        "Cricket board names a new head coach | false",
      })
  void keepsAiCompanyNewsAndDropsTheRest(String title, boolean keep) {
    assertThat(filter.evaluate(title, null, false).keep()).isEqualTo(keep);
  }
}
