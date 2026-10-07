package com.qbits.sources.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SourcesFileParserTest {

  private final SourcesFileParser parser = new SourcesFileParser(Duration.ofMinutes(15));

  private static final String VALID =
      """
      sources:
        - id: example-ai
          name: Example AI
          homepage: https://example.com/ai
          feedUrl: https://example.com/ai/feed.xml
          type: rss
          aiNative: true
          region: india
          sectionHint: india-ai
          intervalMinutes: 20
          termsUrl: https://example.com/terms
          termsReviewedOn: 2026-10-03
          enabled: true
        - id: example-off
          name: Example Off
          homepage: https://example.org
          feedUrl: https://example.org/atom
          type: atom
          aiNative: false
          region: global
          termsUrl: null
          termsReviewedOn: null
          enabled: false
      """;

  @Test
  void loadsAllFieldsOfAValidFile() { // 001 R1.1
    List<Source> sources = parser.parse(VALID);

    assertThat(sources).hasSize(2);
    Source first = sources.getFirst();
    assertThat(first.id()).isEqualTo("example-ai");
    assertThat(first.feedUrl()).hasToString("https://example.com/ai/feed.xml");
    assertThat(first.type()).isEqualTo(SourceType.RSS);
    assertThat(first.region()).isEqualTo(Region.INDIA);
    assertThat(first.aiNative()).isTrue();
    assertThat(first.hint()).contains("india-ai");
    assertThat(first.intervalOverride()).contains(Duration.ofMinutes(20));
    assertThat(first.termsReviewedOn()).isEqualTo(LocalDate.of(2026, 10, 3));
    assertThat(sources.get(1).type()).isEqualTo(SourceType.ATOM);
    assertThat(sources.get(1).enabled()).isFalse();
  }

  @Test
  void rejectsMissingRequiredFieldNamingEntryAndField() { // 001 R1.2
    String yaml = VALID.replace("    name: Example AI\n", "");

    assertThatThrownBy(() -> parser.parse(yaml))
        .isInstanceOf(SourceConfigException.class)
        .hasMessageContaining("source 'example-ai': field 'name' is required");
  }

  @Test
  void rejectsDuplicateIds() { // 001 R1.2
    String yaml = VALID.replace("id: example-off", "id: example-ai");

    assertThatThrownBy(() -> parser.parse(yaml))
        .hasMessageContaining("source 'example-ai': field 'id' is a duplicate");
  }

  @Test
  void rejectsInvalidUrls() { // 001 R1.2
    String yaml = VALID.replace("https://example.com/ai/feed.xml", "ftp://example.com/feed");

    assertThatThrownBy(() -> parser.parse(yaml))
        .hasMessageContaining(
            "source 'example-ai': field 'feedUrl' must be an absolute http(s) URL");
  }

  @Test
  void rejectsEnabledSourceWithoutTermsReview() { // 001 R1.4
    String yaml =
        VALID
            .replace("termsUrl: https://example.com/terms", "termsUrl: null")
            .replace("termsReviewedOn: 2026-10-03", "termsReviewedOn: null");

    assertThatThrownBy(() -> parser.parse(yaml))
        .isInstanceOfSatisfying(
            SourceConfigException.class,
            e ->
                assertThat(e.problems())
                    .containsExactly(
                        "source 'example-ai': field 'termsUrl' is required when enabled is true",
                        "source 'example-ai': field 'termsReviewedOn' is required when enabled is true"));
  }

  @Test
  void disabledSourceMayLackTermsReview() { // 001 R1.4
    assertThat(parser.parse(VALID)).extracting(Source::id).contains("example-off");
  }

  @Test
  void officialIsOptionalAndDefaultsToFalse() { // 008 R2.6
    String yaml = VALID.replace("intervalMinutes: 20", "intervalMinutes: 20\n    official: true");

    List<Source> sources = parser.parse(yaml);

    assertThat(sources.get(0).official()).isTrue();
    assertThat(sources.get(1).official()).isFalse();
  }

  @Test
  void rejectsANonBooleanOfficial() { // 008 R2.6
    String yaml = VALID.replace("intervalMinutes: 20", "intervalMinutes: 20\n    official: maybe");

    assertThatThrownBy(() -> parser.parse(yaml)).hasMessageContaining("'official'");
  }

  @Test
  void rejectsIntervalBelowFifteenMinutes() { // 001 R2.1
    String yaml = VALID.replace("intervalMinutes: 20", "intervalMinutes: 5");

    assertThatThrownBy(() -> parser.parse(yaml)).hasMessageContaining("must be at least 15");
  }

  @Test
  void minimumIntervalComesFromTheIngestionSetting() { // 001 R2.1
    SourcesFileParser stricter = new SourcesFileParser(Duration.ofMinutes(30));

    assertThatThrownBy(() -> stricter.parse(VALID)).hasMessageContaining("must be at least 30");
  }

  @Test
  void rejectsUnknownSectionHint() {
    String yaml = VALID.replace("sectionHint: india-ai", "sectionHint: sports");

    assertThatThrownBy(() -> parser.parse(yaml)).hasMessageContaining("'sectionHint'");
  }

  @Test
  void reportsEveryProblemAtOnce() { // 001 R1.2
    String yaml = VALID.replace("    name: Example AI\n", "").replace("type: atom", "type: html");

    assertThatThrownBy(() -> parser.parse(yaml))
        .isInstanceOfSatisfying(
            SourceConfigException.class, e -> assertThat(e.problems()).hasSize(2));
  }

  @Test
  void projectSourcesFileIsValid() throws Exception { // 001 R1.1, R1.2, R1.4
    String text = Files.readString(Path.of("../config/sources.yml"));

    List<Source> sources = parser.parse(text);

    assertThat(sources).hasSize(26);
  }

  private static final String IMAGES =
      """
          images:
            basis: press_kit
            evidence: https://example.com/press
            reviewedOn: 2026-10-07
            credit: Example AI press kit
      """;

  @Test
  void readsARecordedImagePermission() { // 009 R3.1
    String yaml = VALID.replace("    enabled: true\n", "    enabled: true\n" + IMAGES);

    Source source = parser.parse(yaml).getFirst();

    assertThat(source.imagePermission())
        .contains(
            new ImagePermission(
                ImagePermission.Basis.PRESS_KIT,
                java.net.URI.create("https://example.com/press"),
                LocalDate.parse("2026-10-07"),
                "Example AI press kit"));
    assertThat(parser.parse(VALID).getFirst().imagePermission()).isEmpty();
  }

  @Test
  void rejectsAnImagePermissionWithoutEvidence() { // 009 R3.1
    String yaml =
        VALID.replace(
            "    enabled: true\n",
            "    enabled: true\n"
                + IMAGES.replace("      evidence: https://example.com/press\n", ""));

    assertThatThrownBy(() -> parser.parse(yaml))
        .isInstanceOf(SourceConfigException.class)
        .hasMessageContaining("source 'example-ai.images': field 'evidence' is required");
  }
}
