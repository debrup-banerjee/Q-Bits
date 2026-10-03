package com.qbits.ingestion.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ItemNormaliserTest {

  private static final Instant FETCHED = Instant.parse("2026-10-03T06:00:00Z");
  private final ItemNormaliser normaliser = new ItemNormaliser(Duration.ofHours(72));

  @Test
  void stripsHtmlAndCollapsesWhitespaceInExcerpt() { // 001 R4.2
    NormalisedEntry e =
        accept(
            raw(
                "Title",
                "https://example.com/a",
                "<p>New   <b>model</b>&nbsp;released.</p>\n<img src='x.png'><script>evil()</script>"));

    assertThat(e.excerpt()).isEqualTo("New model released.");
  }

  @Test
  void truncatesLongExcerptAtWordBoundaryWithEllipsis() { // 001 R4.2
    String words = "word ".repeat(100);

    String excerpt = accept(raw("Title", "https://example.com/a", words)).excerpt();

    assertThat(excerpt).hasSizeLessThanOrEqualTo(300).endsWith("word…");
  }

  @Test
  void keepsShortExcerptUnchanged() { // 001 R4.2
    assertThat(accept(raw("T", "https://example.com/a", "Short.")).excerpt()).isEqualTo("Short.");
  }

  @Test
  void missingDescriptionGivesNullExcerpt() {
    assertThat(accept(raw("T", "https://example.com/a", null)).excerpt()).isNull();
  }

  @ParameterizedTest // 001 R4.3
  @CsvSource({
    "https://Example.COM/a/b?utm_source=x&id=7#frag, https://example.com/a/b?id=7",
    "https://example.com/a?fbclid=1&gclid=2, https://example.com/a",
    "HTTPS://example.com:443/a, https://example.com/a",
    "http://example.com:8080/a?utm_medium=y, http://example.com:8080/a",
    "https://example.com, https://example.com/",
  })
  void buildsCanonicalUrl(String link, String expected) {
    assertThat(accept(raw("T", link, null)).canonicalUrl()).isEqualTo(expected);
  }

  @Test
  void usesFetchTimeAndMarksEstimatedWhenNoDate() { // 001 R4.4
    NormalisedEntry e = accept(new RawEntry("T", "https://example.com/a", null, null, null));

    assertThat(e.publishedAt()).isEqualTo(FETCHED);
    assertThat(e.dateEstimated()).isTrue();
  }

  @Test
  void fallsBackToUpdatedDate() { // 001 R4.4
    Instant updated = FETCHED.minusSeconds(600);

    NormalisedEntry e = accept(new RawEntry("T", "https://example.com/a", null, null, updated));

    assertThat(e.publishedAt()).isEqualTo(updated);
    assertThat(e.dateEstimated()).isFalse();
  }

  @Test
  void dropsEntryWithoutTitle() { // 001 R4.5
    assertThat(normaliser.normalise(raw("  <b></b> ", "https://example.com/a", null), FETCHED))
        .isInstanceOf(NormaliseResult.Malformed.class);
  }

  @ParameterizedTest // 001 R4.5
  @CsvSource(
      value = {"NULL", "''", "not a url", "ftp://example.com/a", "/relative/path"},
      nullValues = "NULL")
  void dropsEntryWithoutUsableLink(String link) {
    assertThat(normaliser.normalise(raw("Title", link, null), FETCHED))
        .isInstanceOf(NormaliseResult.Malformed.class);
  }

  @Test
  void dropsEntriesOlderThan72Hours() { // 001 R9.1
    Instant old = FETCHED.minus(Duration.ofHours(72)).minusSeconds(1);
    Instant edge = FETCHED.minus(Duration.ofHours(72));

    assertThat(
            normaliser.normalise(
                new RawEntry("T", "https://example.com/a", null, old, null), FETCHED))
        .isInstanceOf(NormaliseResult.TooOld.class);
    assertThat(
            normaliser.normalise(
                new RawEntry("T", "https://example.com/a", null, edge, null), FETCHED))
        .isInstanceOf(NormaliseResult.Accepted.class);
  }

  @Test
  void titleKeyIgnoresCaseAndPunctuation() { // 001 R6.2
    assertThat(accept(raw("OpenAI’s New Model: GPT-6!", "https://example.com/a", null)).titleKey())
        .isEqualTo("openai s new model gpt 6");
  }

  private NormalisedEntry accept(RawEntry raw) {
    NormaliseResult result = normaliser.normalise(raw, FETCHED);
    assertThat(result).isInstanceOf(NormaliseResult.Accepted.class);
    return ((NormaliseResult.Accepted) result).entry();
  }

  private static RawEntry raw(String title, String link, String description) {
    return new RawEntry(title, link, description, FETCHED.minusSeconds(3600), null);
  }
}
