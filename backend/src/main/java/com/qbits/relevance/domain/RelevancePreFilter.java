package com.qbits.relevance.domain;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Cheap first pass that keeps likely AI stories (spec 001 R5). Uses only the title and excerpt.
 * Spec 002 makes the final call. Pure: no I/O, no framework.
 */
public final class RelevancePreFilter {

  /** Score and decision for one item. Score is capped at 1.0. */
  public record Result(double score, boolean keep) {}

  private record CompiledKeyword(Pattern pattern, double weight) {}

  private final List<CompiledKeyword> keywords;
  private final double threshold;

  public RelevancePreFilter(List<Keyword> keywords, double threshold) {
    this.keywords =
        keywords.stream().map(k -> new CompiledKeyword(wholeWord(k.term()), k.weight())).toList();
    this.threshold = threshold;
  }

  public Result evaluate(String title, String excerpt, boolean aiNativeSource) {
    if (aiNativeSource) {
      return new Result(1.0, true); // R5.1
    }
    String text = (title == null ? "" : title) + "\n" + (excerpt == null ? "" : excerpt);
    double score = 0;
    for (CompiledKeyword k : keywords) {
      if (k.pattern().matcher(text).find()) {
        score += k.weight();
      }
    }
    return new Result(Math.min(score, 1.0), score >= threshold); // R5.2
  }

  /** Whole-word, case-insensitive match so "AI" matches "AI chips" but not "said" (R5.3). */
  private static Pattern wholeWord(String term) {
    return Pattern.compile(
        "(?<![\\p{L}\\p{N}])" + Pattern.quote(term) + "(?![\\p{L}\\p{N}])",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  }
}
