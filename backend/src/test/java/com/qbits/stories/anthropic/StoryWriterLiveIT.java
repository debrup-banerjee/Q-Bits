package com.qbits.stories.anthropic;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.qbits.stories.StoryWriterProperties;
import com.qbits.stories.domain.KeyTerm;
import com.qbits.stories.domain.Section;
import com.qbits.stories.domain.StoryDraft;
import com.qbits.stories.domain.StoryInput;
import com.qbits.stories.domain.StoryValidator;
import com.qbits.stories.domain.ValidationFailure;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Opt-in live run against the real service (spec 002 T7). Excluded from the normal build. Run:
 *
 * <pre>
 * ANTHROPIC_API_KEY=... QBITS_STORY_MODEL=... mvn -f backend/pom.xml verify -Plive
 * </pre>
 *
 * Writes every result to {@code target/live-samples.md} for the editorial review against
 * steering/editorial-style.md. The items are fictional examples, one set per section.
 */
class StoryWriterLiveIT {

  @Test
  void writeSamplesForReview() throws Exception {
    String key = System.getenv("ANTHROPIC_API_KEY");
    String model = System.getenv("QBITS_STORY_MODEL");
    assumeTrue(
        key != null && !key.isBlank() && model != null && !model.isBlank(),
        "set ANTHROPIC_API_KEY and QBITS_STORY_MODEL to run");

    JsonMapper json = JsonMapper.builder().build();
    List<Map<String, String>> items;
    try (InputStream in = getClass().getResourceAsStream("/live/sample-items.json")) {
      items = json.readValue(in, new TypeReference<>() {});
    }
    StoryWriterProperties props =
        new StoryWriterProperties(
            "anthropic",
            model,
            "v1",
            800,
            500,
            1,
            20,
            Duration.ofSeconds(60),
            Duration.ofMinutes(2),
            Duration.ofMinutes(30));
    StoryValidator validator = new StoryValidator();

    StringBuilder report =
        new StringBuilder("# Live story samples\n\nModel: `" + model + "` · prompt v1\n\n");
    long inTokens = 0;
    long outTokens = 0;
    int sectionMatches = 0;
    int valid = 0;
    AnthropicClient client = AnthropicOkHttpClient.builder().apiKey(key).maxRetries(2).build();
    try {
      AnthropicStoryWriter writer = new AnthropicStoryWriter(client, props, json);
      for (Map<String, String> item : items) {
        StoryInput input =
            new StoryInput(
                item.get("title"),
                item.get("excerpt"),
                item.get("sourceName"),
                item.get("region"),
                item.get("sectionHint"),
                Instant.parse("2026-10-03T04:00:00Z"));
        StoryDraft draft = writer.write(input, Optional.empty());
        List<ValidationFailure> failures = validator.check(draft, input);
        inTokens += draft.inputTokens();
        outTokens += draft.outputTokens();
        Section expected = Section.valueOf(item.get("section"));
        if (expected == draft.section()) {
          sectionMatches++;
        }
        if (failures.isEmpty()) {
          valid++;
        }
        report
            .append("## ")
            .append(item.get("title"))
            .append("\n\n")
            .append("- Expected section: ")
            .append(expected.displayName())
            .append(" · got: ")
            .append(draft.section() == null ? "—" : draft.section().displayName())
            .append('\n')
            .append("- Validation: ")
            .append(failures.isEmpty() ? "pass" : failures)
            .append("\n\n")
            .append("**")
            .append(draft.headline())
            .append("**\n\n")
            .append(draft.summary())
            .append("\n\n");
        for (KeyTerm t : draft.keyTerms()) {
          report.append("- *").append(t.term()).append("*: ").append(t.meaning()).append('\n');
        }
        report.append("\n---\n\n");
      }
    } finally {
      client.close();
    }
    report
        .append("## Totals\n\n")
        .append("- Sections matched: ")
        .append(sectionMatches)
        .append("/")
        .append(items.size())
        .append('\n')
        .append("- Passed validation first time: ")
        .append(valid)
        .append("/")
        .append(items.size())
        .append('\n')
        .append("- Tokens: ")
        .append(inTokens)
        .append(" in, ")
        .append(outTokens)
        .append(" out")
        .append(" (≈ per 100 stories: ")
        .append(inTokens * 100 / items.size())
        .append(" in, ")
        .append(outTokens * 100 / items.size())
        .append(" out)\n");
    Files.writeString(Path.of("target/live-samples.md"), report);
  }
}
