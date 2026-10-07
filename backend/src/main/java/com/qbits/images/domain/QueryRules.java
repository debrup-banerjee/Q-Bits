package com.qbits.images.domain;

import com.qbits.stories.domain.KeyTerm;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks a model-written photo search phrase (spec 009 R2.2): short, plain words only, and none of
 * the names in the story. A phrase that fails is replaced by {@link ImageQueryFallback}.
 */
public final class QueryRules {

  public static final int MAX_LENGTH = 60;
  private static final Pattern PLAIN = Pattern.compile("[a-z][a-z \\-]{2,59}");

  private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}.\\-]*");

  private QueryRules() {}

  /**
   * Words that look like names: every capitalised word of the headline (the first one too, since
   * headlines often start with a company name), and every word of a key term that starts with a
   * capital or contains a digit (product and model names). A false match only means the safe
   * fallback phrase is used.
   */
  public static Set<String> namesIn(String headline, List<KeyTerm> keyTerms) {
    Set<String> names = new LinkedHashSet<>();
    addNames(names, headline);
    for (KeyTerm t : keyTerms) {
      addNames(names, t.term());
    }
    return names;
  }

  private static void addNames(Set<String> names, String text) {
    if (text == null) {
      return;
    }
    Matcher m = WORD.matcher(text);
    while (m.find()) {
      if (looksLikeName(m.group())) {
        names.add(m.group());
      }
    }
  }

  private static boolean looksLikeName(String w) {
    return Character.isUpperCase(w.codePointAt(0)) || w.chars().anyMatch(Character::isDigit);
  }

  /**
   * The cleaned phrase, or empty if it breaks a rule. {@code names} are words that must not appear
   * (company, product and people names taken from the story's key terms and headline).
   */
  public static Optional<String> clean(String phrase, Iterable<String> names) {
    if (phrase == null) {
      return Optional.empty();
    }
    String q = phrase.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    if (q.length() > MAX_LENGTH || !PLAIN.matcher(q).matches()) {
      return Optional.empty();
    }
    String padded = " " + q + " ";
    for (String name : names) {
      String n = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
      if (n.length() >= 3 && padded.contains(" " + n + " ")) {
        return Optional.empty();
      }
    }
    return Optional.of(q);
  }
}
