package com.qbits.resources.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.qbits.common.links.LinkOrigin;
import org.junit.jupiter.api.Test;

class ResourceRelevanceTest {

  private final ResourceRelevance relevance = new ResourceRelevance();
  private static final Candidate KESTREL =
      new Candidate(
          ResourceType.CODE,
          ResourceHost.GITHUB,
          "https://github.com/kestrel-ai/kestrel-70b",
          "kestrel-ai/kestrel-70b",
          false);
  private static final Candidate WIDGETS =
      new Candidate(
          ResourceType.CODE,
          ResourceHost.GITHUB,
          "https://github.com/newsco/share-widgets",
          "newsco/share-widgets",
          false);

  @Test
  void keepsLinksFromEntryLinksAndDescription() { // 005 R3.1
    assertThat(relevance.keep(WIDGETS, LinkOrigin.ENTRY_LINK, "Anything", null)).isTrue();
    assertThat(relevance.keep(WIDGETS, LinkOrigin.DESCRIPTION, "Anything", null)).isTrue();
  }

  @Test
  void keepsContentLinksOnlyWhenTheProjectIsNamed() { // 005 R3.2
    assertThat(
            relevance.keep(KESTREL, LinkOrigin.CONTENT, "Kestrel 70B matches closed models", null))
        .isTrue();
    assertThat(
            relevance.keep(WIDGETS, LinkOrigin.CONTENT, "Kestrel 70B matches closed models", null))
        .isFalse();
  }

  @Test
  void matchesWholeWordsOnly() {
    Candidate ai =
        new Candidate(
            ResourceType.CODE, ResourceHost.GITHUB, "https://github.com/x/aid", "x/aid", false);
    assertThat(relevance.keep(ai, LinkOrigin.CONTENT, "A new AI aide", null)).isFalse();
  }
}
