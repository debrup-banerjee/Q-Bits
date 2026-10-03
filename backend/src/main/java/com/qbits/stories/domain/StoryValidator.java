package com.qbits.stories.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Machine checks applied to every draft before it can be published (spec 002 R4–R8). Pure: no I/O,
 * no framework. Rules that need judgement (story shape, hedging, explaining every term) are handled
 * by the prompt and by review.
 */
public final class StoryValidator {

  static final int MAX_HEADLINE_WORDS = 12;
  static final int MIN_SUMMARY_WORDS = 80;
  static final int MIN_SUMMARY_WORDS_THIN_INPUT = 40;
  static final int MAX_SUMMARY_WORDS = 120;
  static final int THIN_EXCERPT_CHARS = 150;
  static final int MAX_KEY_TERMS = 5;
  static final int MAX_MEANING_WORDS = 20;
  static final int OVERLAP_WORDS = 8;
  static final int QUOTE_WORDS = 4;

  private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)*");
  private static final Pattern QUOTED = Pattern.compile("[\"“”‘’']([^\"“”‘’']{3,})[\"“”‘’']");

  public List<ValidationFailure> check(StoryDraft draft, StoryInput input) {
    List<ValidationFailure> failures = new ArrayList<>();
    if (!draft.isAi()) {
      if (isBlank(draft.reason())) {
        failures.add(new ValidationFailure("SHAPE", "reason is required when isAi is false"));
      }
      return failures;
    }
    if (draft.section() == null || isBlank(draft.headline()) || isBlank(draft.summary())) {
      failures.add(
          new ValidationFailure("SHAPE", "section, headline and summary are required when isAi"));
      return failures;
    }

    String sourceText = join(input.title(), input.excerpt());

    int headlineWords = words(draft.headline()).size();
    if (headlineWords > MAX_HEADLINE_WORDS) {
      failures.add(
          new ValidationFailure(
              "HEADLINE_LEN",
              "headline has " + headlineWords + " words; use at most " + MAX_HEADLINE_WORDS));
    }

    int summaryWords = words(draft.summary()).size();
    boolean thin = input.excerpt() == null || input.excerpt().length() < THIN_EXCERPT_CHARS;
    int min = thin ? MIN_SUMMARY_WORDS_THIN_INPUT : MIN_SUMMARY_WORDS;
    if (summaryWords < min || summaryWords > MAX_SUMMARY_WORDS) {
      failures.add(
          new ValidationFailure(
              "SUMMARY_LEN",
              "summary has "
                  + summaryWords
                  + " words; it must have "
                  + min
                  + "–"
                  + MAX_SUMMARY_WORDS));
    }

    if (!namesSource(draft.summary(), input.sourceName())) {
      failures.add(
          new ValidationFailure(
              "SOURCE_NAMED", "summary must name the source " + input.sourceName()));
    }

    checkTerms(draft, sourceText, failures);
    checkNumbers(draft, sourceText, failures);
    checkOverlap(draft.summary(), sourceText, failures);
    checkQuotes(draft.summary(), input.excerpt(), failures);
    return failures;
  }

  private static void checkTerms(StoryDraft draft, String sourceText, List<ValidationFailure> out) {
    List<KeyTerm> terms = draft.keyTerms();
    if (terms.size() > MAX_KEY_TERMS) {
      out.add(new ValidationFailure("TERMS", "use at most " + MAX_KEY_TERMS + " key terms"));
    }
    String haystack = (sourceText + "\n" + draft.summary()).toLowerCase(Locale.ROOT);
    for (KeyTerm t : terms) {
      if (isBlank(t.term()) || isBlank(t.meaning())) {
        out.add(new ValidationFailure("TERMS", "every key term needs a term and a meaning"));
        continue;
      }
      if (words(t.meaning()).size() > MAX_MEANING_WORDS) {
        out.add(
            new ValidationFailure(
                "TERMS",
                "meaning of '" + t.term() + "' must be at most " + MAX_MEANING_WORDS + " words"));
      }
      if (!haystack.contains(t.term().toLowerCase(Locale.ROOT))) {
        out.add(
            new ValidationFailure(
                "TERMS", "key term '" + t.term() + "' does not appear in the story or the source"));
      }
    }
  }

  /** Every number in the headline and summary must come from the input (R6.1). */
  private static void checkNumbers(
      StoryDraft draft, String sourceText, List<ValidationFailure> out) {
    Set<String> allowed = numbers(sourceText);
    Set<String> invented = new HashSet<>();
    for (String n : numbers(draft.headline() + "\n" + draft.summary())) {
      if (!allowed.contains(n)) {
        invented.add(n);
      }
    }
    if (!invented.isEmpty()) {
      out.add(
          new ValidationFailure(
              "NUMBERS",
              "these numbers are not in the source: "
                  + String.join(", ", invented.stream().sorted().toList())));
    }
  }

  /**
   * No run of 8 words may be copied from the source (R7.1). Product names are not excluded
   * separately: names that long do not occur in practice.
   */
  private static void checkOverlap(String summary, String sourceText, List<ValidationFailure> out) {
    Set<String> sourceGrams = ngrams(words(sourceText), OVERLAP_WORDS);
    for (String gram : ngrams(words(summary), OVERLAP_WORDS)) {
      if (sourceGrams.contains(gram)) {
        out.add(
            new ValidationFailure(
                "OVERLAP", "rewrite in your own words; copied: \"" + gram + "\""));
        return;
      }
    }
  }

  /** No quoting the source (R7.2). */
  private static void checkQuotes(String summary, String excerpt, List<ValidationFailure> out) {
    if (excerpt == null) {
      return;
    }
    String normalisedExcerpt = String.join(" ", words(excerpt));
    Matcher m = QUOTED.matcher(summary);
    while (m.find()) {
      List<String> quoted = words(m.group(1));
      if (quoted.size() >= QUOTE_WORDS && normalisedExcerpt.contains(String.join(" ", quoted))) {
        out.add(new ValidationFailure("QUOTES", "do not quote the source"));
        return;
      }
    }
  }

  static boolean namesSource(String summary, String sourceName) {
    if (sourceName == null) {
      return true;
    }
    String lower = summary.toLowerCase(Locale.ROOT);
    String full = sourceName.toLowerCase(Locale.ROOT).trim();
    String main = full.replaceAll("\\s*\\(.*\\)\\s*$", "").trim(); // "Mint (Live)" -> "mint"
    return lower.contains(full) || (!main.isEmpty() && lower.contains(main));
  }

  static List<String> words(String text) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    String cleaned = text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    return cleaned.isEmpty() ? List.of() : List.of(cleaned.split(" "));
  }

  static Set<String> numbers(String text) {
    Set<String> out = new HashSet<>();
    Matcher m = NUMBER.matcher(text == null ? "" : text);
    while (m.find()) {
      out.add(m.group().replace(",", "")); // "1,000" == "1000"
    }
    return out;
  }

  private static Set<String> ngrams(List<String> words, int n) {
    Set<String> grams = new HashSet<>();
    for (int i = 0; i + n <= words.size(); i++) {
      grams.add(String.join(" ", words.subList(i, i + n)));
    }
    return grams;
  }

  private static String join(String a, String b) {
    return (a == null ? "" : a) + "\n" + (b == null ? "" : b);
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
