package com.qbits.images.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.qbits.stories.domain.KeyTerm;
import com.qbits.stories.domain.Section;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImageQueryFallbackTest {

  @Test
  void usesTheFirstKeyTermWithAKnownVisual() { // 009 R2.3
    List<KeyTerm> terms =
        List.of(new KeyTerm("Funding round", "Money raised."), new KeyTerm("GPU", "A chip."));

    assertThat(ImageQueryFallback.queryFor(Section.GLOBAL_AI_TECH, terms))
        .isEqualTo("city skyline office towers");
  }

  @Test
  void fallsBackToTheSectionScene() { // 009 R2.3
    assertThat(ImageQueryFallback.queryFor(Section.INNOVATIONS_RESEARCH, List.of()))
        .isEqualTo("science laboratory");
    for (Section s : Section.values()) {
      assertThat(ImageQueryFallback.queryFor(s, List.of())).isNotBlank();
    }
  }
}
