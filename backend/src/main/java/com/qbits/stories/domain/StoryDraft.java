package com.qbits.stories.domain;

import java.util.List;

/**
 * What the summary service returned for one item. When {@code isAi} is false only {@code reason}
 * matters. Usage figures are for traceability (spec 002 R10.1).
 */
public record StoryDraft(
    boolean isAi,
    String reason,
    Section section,
    String headline,
    String summary,
    List<KeyTerm> keyTerms,
    String model,
    int inputTokens,
    int outputTokens) {

  public StoryDraft {
    keyTerms = keyTerms == null ? List.of() : List.copyOf(keyTerms);
  }
}
