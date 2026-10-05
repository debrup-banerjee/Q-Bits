package com.qbits.stories.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class StoryValidatorTest {

  private final StoryValidator validator = new StoryValidator();

  private static final String EXCERPT =
      "Acme Labs released Atlas 2, a large language model that can read 1,000 pages at once and"
          + " runs on 8 GPUs, the company said on Friday in a blog post for developers.";

  private static final StoryInput INPUT =
      new StoryInput(
          "Acme Labs launches Atlas 2 model",
          EXCERPT,
          "TechCrunch",
          "GLOBAL",
          null,
          Instant.parse("2026-10-03T04:00:00Z"));

  /** 87 words, names the source, numbers from input, no long copied runs. */
  private static final String GOOD_SUMMARY =
      "Imagine handing an assistant a whole shelf of books and getting answers in seconds."
          + " According to TechCrunch, Acme Labs has a new large language model (an AI trained on huge"
          + " amounts of text) called Atlas 2. It can take in about 1000 pages in one go. It needs"
          + " 8 graphics processing units (GPUs), the powerful chips that do the heavy maths. This"
          + " matters because longer reading means fewer chopped-up questions for people who work"
          + " with big reports. Developers are the first audience, so watch for tools built on it in"
          + " the coming weeks.";

  private static StoryDraft draft(String headline, String summary, List<KeyTerm> terms) {
    return new StoryDraft(
        true, "AI model launch", Section.GLOBAL_AI_TECH, headline, summary, terms, "m", 10, 10);
  }

  private static StoryDraft good() {
    return draft(
        "New AI model can read a thousand pages at once",
        GOOD_SUMMARY,
        List.of(
            new KeyTerm("large language model", "An AI trained on lots of text to read and write."),
            new KeyTerm("GPUs", "Powerful chips that do many sums at the same time.")));
  }

  private List<String> rules(StoryDraft d, StoryInput in) {
    return validator.check(d, in).stream().map(ValidationFailure::rule).toList();
  }

  @Test
  void goodDraftPasses() {
    assertThat(validator.check(good(), INPUT)).isEmpty();
  }

  @Test
  void notAiNeedsOnlyAReason() { // 002 R2.1, R2.2
    StoryDraft notAi = new StoryDraft(false, "About football", null, null, null, null, "m", 1, 1);
    assertThat(validator.check(notAi, INPUT)).isEmpty();
    StoryDraft noReason = new StoryDraft(false, " ", null, null, null, null, "m", 1, 1);
    assertThat(rules(noReason, INPUT)).containsExactly("SHAPE");
  }

  @Test
  void missingFieldsFailShape() { // 002 R3.1, R8.1
    StoryDraft d = new StoryDraft(true, "r", null, "h", GOOD_SUMMARY, List.of(), "m", 1, 1);
    assertThat(rules(d, INPUT)).containsExactly("SHAPE");
  }

  @Test
  void headlineOverTwelveWordsFails() { // 002 R4.1
    StoryDraft d =
        draft(
            "one two three four five six seven eight nine ten eleven twelve thirteen",
            GOOD_SUMMARY,
            List.of());
    assertThat(rules(d, INPUT)).contains("HEADLINE_LEN");
    StoryDraft ok =
        draft(
            "one two three four five six seven eight nine ten eleven twelve",
            GOOD_SUMMARY,
            List.of());
    assertThat(rules(ok, INPUT)).doesNotContain("HEADLINE_LEN");
  }

  @Test
  void summaryLengthDependsOnExcerptLength() { // 002 R4.2
    String fiftyWords = "According to TechCrunch " + "word ".repeat(47);
    assertThat(rules(draft("H", fiftyWords, List.of()), INPUT)).contains("SUMMARY_LEN");

    StoryInput thin =
        new StoryInput("Acme launches Atlas", "Short teaser.", "TechCrunch", "GLOBAL", null, null);
    assertThat(rules(draft("H", fiftyWords, List.of()), thin)).doesNotContain("SUMMARY_LEN");

    String tooLong = "According to TechCrunch " + "word ".repeat(118);
    assertThat(rules(draft("H", tooLong, List.of()), thin)).contains("SUMMARY_LEN");
  }

  @Test
  void summaryMustNameTheSource() { // 002 R4.4
    StoryDraft d = draft("H", GOOD_SUMMARY.replace("According to TechCrunch, ", ""), List.of());
    assertThat(rules(d, INPUT)).contains("SOURCE_NAMED");
  }

  @Test
  void sourceNameInBracketsMayBeShortened() {
    assertThat(StoryValidator.namesSource("Google AI said...", "Google AI (The Keyword)")).isTrue();
  }

  @Test
  void keyTermsAreLimitedAndMustAppear() { // 002 R5.1, R5.3
    List<KeyTerm> six =
        List.of(
            new KeyTerm("GPUs", "chips"),
            new KeyTerm("GPUs", "chips"),
            new KeyTerm("GPUs", "chips"),
            new KeyTerm("GPUs", "chips"),
            new KeyTerm("GPUs", "chips"),
            new KeyTerm("GPUs", "chips"));
    assertThat(rules(draft("H", GOOD_SUMMARY, six), INPUT)).contains("TERMS");

    List<KeyTerm> absent = List.of(new KeyTerm("quantum computing", "Very fast computers."));
    assertThat(rules(draft("H", GOOD_SUMMARY, absent), INPUT)).contains("TERMS");

    List<KeyTerm> wordy = List.of(new KeyTerm("GPUs", "word ".repeat(21)));
    assertThat(rules(draft("H", GOOD_SUMMARY, wordy), INPUT)).contains("TERMS");
  }

  @Test
  void numbersMustComeFromTheSource() { // 002 R6.1
    assertThat(rules(good(), INPUT)).doesNotContain("NUMBERS"); // "1000" matches "1,000"
    StoryDraft invented = draft("H", GOOD_SUMMARY.replace("8 graphics", "16 graphics"), List.of());
    assertThat(validator.check(invented, INPUT))
        .anySatisfy(f -> assertThat(f.toString()).contains("NUMBERS").contains("16"));
  }

  @Test
  void numbersAreComparedWithTheirScaleWords() { // 002 R6.1
    StoryInput funding =
        new StoryInput(
            "Acme Labs raises $5 million",
            "The start-up raised $5 million and plans a $2.5bn data centre with 300 staff.",
            "TechCrunch",
            "GLOBAL",
            null,
            Instant.parse("2026-10-03T04:00:00Z"));

    assertThat(numberFailures("It raised 5 million dollars.", funding)).isEmpty();
    assertThat(numberFailures("It raised $5m and plans a 2.5 billion dollar site.", funding))
        .isEmpty(); // other spellings of the same scale
    assertThat(numberFailures("About 300 people will work there.", funding)).isEmpty();
    assertThat(numberFailures("It has 5 people.", funding)).isEmpty(); // bare digits from input
    assertThat(numberFailures("It raised 5 billion dollars.", funding))
        .singleElement()
        .satisfies(f -> assertThat(f).contains("5 billion"));
    assertThat(numberFailures("It hired 300 thousand staff.", funding))
        .singleElement()
        .satisfies(f -> assertThat(f).contains("300 thousand"));
  }

  private List<String> numberFailures(String sentence, StoryInput in) {
    return validator.check(draft("H", sentence, List.of()), in).stream()
        .filter(f -> f.rule().equals("NUMBERS"))
        .map(ValidationFailure::toString)
        .toList();
  }

  @Test
  void notesFitTheStoredLimitAsOneLine() { // 002 R2.2, R8.3
    assertThat(StoryNote.of("  Mostly about\n football\t\u0000 "))
        .isEqualTo("Mostly about football");
    String cut = StoryNote.of("x".repeat(299) + "😀" + "tail");
    assertThat(cut.codePointCount(0, cut.length())).isEqualTo(StoryNote.MAX_CHARS);
    assertThat(cut).endsWith("😀");
    assertThat(StoryNote.of(null)).isNull();
  }

  @Test
  void copyingEightWordsFromTheSourceFails() { // 002 R7.1
    String copied = GOOD_SUMMARY + " It can read 1,000 pages at once and runs on 8 GPUs.";
    assertThat(rules(draft("H", copied, List.of()), INPUT)).contains("OVERLAP");
    String sevenWords =
        GOOD_SUMMARY.replace("It can take in about", "It can read 1,000 pages at once,");
    assertThat(rules(draft("H", sevenWords, List.of()), INPUT)).doesNotContain("OVERLAP");
  }

  @Test
  void quotingTheSourceFails() { // 002 R7.2
    String quoted = GOOD_SUMMARY + " The company called it “a blog post for developers”.";
    assertThat(rules(draft("H", quoted, List.of()), INPUT)).contains("QUOTES");
    String singleQuoted = GOOD_SUMMARY + " The company called it 'a blog post for developers'.";
    assertThat(rules(draft("H", singleQuoted, List.of()), INPUT)).contains("QUOTES");
  }

  @Test
  void apostrophesInsideWordsAreNotQuoteMarks() { // 002 R7.2
    String possessives =
        GOOD_SUMMARY
            + " Acme Labs’ model that can read 1,000 pages’ worth of text is the lab’s pick.";
    assertThat(rules(draft("H", possessives, List.of()), INPUT)).doesNotContain("QUOTES");
    String straight =
        GOOD_SUMMARY
            + " Acme Labs' model that can read 1,000 pages' worth of text is the lab's pick.";
    assertThat(rules(draft("H", straight, List.of()), INPUT)).doesNotContain("QUOTES");
    String quotedWithApostrophe =
        GOOD_SUMMARY + " It called the launch 'the company's blog post for developers'.";
    StoryInput withApostrophe =
        new StoryInput(
            INPUT.title(),
            EXCERPT + " It is the company's blog post for developers.",
            INPUT.sourceName(),
            INPUT.region(),
            INPUT.sectionHint(),
            INPUT.publishedAt());
    assertThat(rules(draft("H", quotedWithApostrophe, List.of()), withApostrophe))
        .contains("QUOTES");
  }

  @Test
  void draftsLongerThanTheStoredLimitsFail() { // 002 R4.1, R4.2, R8.1
    String longHeadline = "Extraordinarily ".repeat(12).trim(); // 12 words, 191 characters
    assertThat(validator.check(draft(longHeadline, GOOD_SUMMARY, List.of()), INPUT))
        .anySatisfy(f -> assertThat(f.toString()).contains("HEADLINE_LEN").contains("characters"));

    String longSummary = "According to TechCrunch " + "extraordinarily ".repeat(97); // 100 words
    assertThat(validator.check(draft("H", longSummary, List.of()), INPUT))
        .anySatisfy(f -> assertThat(f.toString()).contains("SUMMARY_LEN").contains("characters"));

    assertThat(rules(good(), INPUT)).doesNotContain("HEADLINE_LEN", "SUMMARY_LEN");
  }
}
