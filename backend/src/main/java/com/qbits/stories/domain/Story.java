package com.qbits.stories.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A validated, published story for one item. */
public record Story(
    UUID itemId,
    Section section,
    String headline,
    String summary,
    List<KeyTerm> keyTerms,
    String model,
    String promptVersion,
    Instant writtenAt,
    int inputTokens,
    int outputTokens,
    int attempts) {

  public Story {
    keyTerms = List.copyOf(keyTerms);
  }
}
