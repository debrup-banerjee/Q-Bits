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

  /** Character limits of the stories table; a longer draft could never be saved. */
  static final int MAX_HEADLINE_CHARS = 120;

  static final int MAX_SUMMARY_CHARS = 1000;
  static final int THIN_EXCERPT_CHARS = 150;
  static final int MAX_KEY_TERMS = 5;
  static final int MAX_MEANING_WORDS = 20;
  static final int OVERLAP_WORDS = 8;
  static final int QUOTE_WORDS = 4;

  /**
   * A number with the scale word that follows it, if any: "5 million", "$2.5bn", "3 crore". A
   * single-letter scale ("5m", "2B") counts only when written straight after the digits.
   */
  private static final Pattern NUMBER =
      Pattern.compile(
          "(\\d+(?:[.,]\\d+)*)(?:\\s*(thousand|million|billion|trillion|lakhs?|crores?|mn|bn|tn)\\b"
              + "|(k|m|b)\\b)?",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern DOUBLE_QUOTED = Pattern.compile("[\"“”]([^\"“”]{3,})[\"“”]");

  /**
   * A single-quoted span. An apostrophe inside or at the end of a word ("OpenAI's", "labs'") is not
   * a quote mark: the opening mark must not follow a letter or digit, the closing one must not be
   * followed by one, and apostrophes between letters may appear inside the span.
   */
  private static final Pattern SINGLE_QUOTED =
      Pattern.compile(
          "(?<![\\p{L}\\p{N}])['‘’]((?:[^'‘’]|(?<=[\\p{L}\\p{N}])['’](?=\\p{L})){3,}?)['’]"
              + "(?![\\p{L}\\p{N}])");

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
    int headlineChars = draft.headline().trim().length();
    if (headlineChars > MAX_HEADLINE_CHARS) {
      failures.add(
          new ValidationFailure(
              "HEADLINE_LEN",
              "headline has " + headlineChars + " characters; use at most " + MAX_HEADLINE_CHARS));
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
    int summaryChars = draft.summary().trim().length();
    if (summaryChars > MAX_SUMMARY_CHARS) {
      failures.add(
          new ValidationFailure(
              "SUMMARY_LEN",
              "summary has " + summaryChars + " characters; use at most " + MAX_SUMMARY_CHARS));
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

  /**
   * Every number in the headline and summary must come from the input (R6.1). A number written with
   * a scale word must appear with the same scale ("5 billion" does not match "5 million"); a bare
   * number matches the same digits with or without one.
   */
  private static void checkNumbers(
      StoryDraft draft, String sourceText, List<ValidationFailure> out) {
    Set<NumberToken> allowed = numbers(sourceText);
    Set<String> allowedDigits = new HashSet<>();
    allowed.forEach(n -> allowedDigits.add(n.digits()));
    Set<String> invented = new HashSet<>();
    for (NumberToken n : numbers(draft.headline() + "\n" + draft.summary())) {
      boolean found =
          n.scale().isEmpty() ? allowedDigits.contains(n.digits()) : allowed.contains(n);
      if (!found) {
        invented.add(n.toString());
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
    for (Pattern p : List.of(DOUBLE_QUOTED, SINGLE_QUOTED)) {
      Matcher m = p.matcher(summary);
      while (m.find()) {
        List<String> quoted = words(m.group(1));
        if (quoted.size() >= QUOTE_WORDS && normalisedExcerpt.contains(String.join(" ", quoted))) {
          out.add(new ValidationFailure("QUOTES", "do not quote the source"));
          return;
        }
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

  /** A number as compared by the NUMBERS rule: its digits and its scale word, if any. */
  record NumberToken(String digits, String scale) {
    @Override
    public String toString() {
      return scale.isEmpty() ? digits : digits + " " + scale;
    }
  }

  static Set<NumberToken> numbers(String text) {
    Set<NumberToken> out = new HashSet<>();
    Matcher m = NUMBER.matcher(text == null ? "" : text);
    while (m.find()) {
      String digits = m.group(1).replace(",", ""); // "1,000" == "1000"
      String word = m.group(2) != null ? m.group(2) : m.group(3);
      out.add(new NumberToken(digits, scale(word)));
    }
    return out;
  }

  /** One spelling per scale: "bn", "b" and "billion" are the same. */
  private static String scale(String word) {
    if (word == null) {
      return "";
    }
    return switch (word.toLowerCase(Locale.ROOT)) {
      case "k", "thousand" -> "thousand";
      case "m", "mn", "million" -> "million";
      case "b", "bn", "billion" -> "billion";
      case "tn", "trillion" -> "trillion";
      case "lakh", "lakhs" -> "lakh";
      case "crore", "crores" -> "crore";
      default -> "";
    };
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
