package com.qbits.images.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.qbits.stories.domain.KeyTerm;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QueryRulesTest {

  @Test
  void keepsAShortPlainPhrase() { // 009 R2.2
    assertThat(QueryRules.clean("  Server Racks in a  Data Center ", Set.of()))
        .contains("server racks in a data center");
  }

  @Test
  void rejectsNamesFromTheStory() { // 009 R2.2
    Set<String> names =
        QueryRules.namesIn(
            "OpenAI releases GPT-5 for Indian schools",
            List.of(new KeyTerm("Context window", "How much text the AI can read.")));

    assertThat(names).contains("OpenAI", "GPT-5", "Indian", "Context").doesNotContain("schools");
    assertThat(QueryRules.clean("indian students with laptops", names)).isEmpty();
    assertThat(QueryRules.clean("students with laptops", names)).contains("students with laptops");
  }

  @Test
  void rejectsPunctuationDigitsAndLongPhrases() { // 009 R2.2
    assertThat(QueryRules.clean("\"chip\" factory", Set.of())).isEmpty();
    assertThat(QueryRules.clean("gpu 5090 board", Set.of())).isEmpty();
    assertThat(QueryRules.clean("a".repeat(61), Set.of())).isEmpty();
    assertThat(QueryRules.clean(null, Set.of())).isEmpty();
  }
}
