package com.qbits.catalog.domain;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Picks the stories shown in the top stories slider (spec 009 R2.1, R2.2). Pure, no I/O. */
public final class TopStoryRules {

  private TopStoryRules() {}

  /** A published story with the number of distinct sources that reported it. */
  public record Candidate(
      UUID id, String sourceId, boolean official, Instant publishedAt, int coverage) {}

  /**
   * Keeps candidates with at least {@code minCoverage} sources, then orders by coverage (most
   * first), an official source, the earliest publish time, and id; returns at most {@code limit}.
   */
  public static List<Candidate> pick(List<Candidate> candidates, int minCoverage, int limit) {
    return candidates.stream()
        .filter(c -> c.coverage() >= minCoverage)
        .sorted(
            Comparator.comparingInt(Candidate::coverage)
                .reversed()
                .thenComparing(Candidate::official, Comparator.reverseOrder())
                .thenComparing(Candidate::publishedAt)
                .thenComparing(Candidate::id))
        .limit(limit)
        .toList();
  }
}
