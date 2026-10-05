package com.qbits.stories.anthropic;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolChoice;
import com.anthropic.models.messages.ToolUseBlock;
import com.qbits.stories.StoryWriter.WriterRejected;
import com.qbits.stories.StoryWriterProperties;
import com.qbits.stories.domain.KeyTerm;
import com.qbits.stories.domain.Section;
import com.qbits.stories.domain.StoryDraft;
import com.qbits.stories.domain.StoryInput;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds the parts of a story request (system prompt, forced {@code write_story} tool, user
 * message) and reads the answer. Shared by the single-call and batch writers so both send exactly
 * the same thing (spec 006 R2.1).
 */
public final class StoryRequests {

  static final String TOOL_NAME = "write_story";

  static final Map<String, Object> SCHEMA_PROPERTIES =
      Map.of(
          "isAi", Map.of("type", "boolean"),
          "reason", Map.of("type", "string", "maxLength", 160),
          "section",
              Map.of(
                  "type",
                  "string",
                  "enum",
                  List.of(
                      "GLOBAL_AI_TECH",
                      "NEW_RELEASES",
                      "WORLD_BUSINESS",
                      "INDIA_AI",
                      "INNOVATIONS_RESEARCH")),
          "headline", Map.of("type", "string"),
          "summary", Map.of("type", "string"),
          "keyTerms",
              Map.of(
                  "type",
                  "array",
                  "maxItems",
                  5,
                  "items",
                  Map.of(
                      "type",
                      "object",
                      "required",
                      List.of("term", "meaning"),
                      "properties",
                      Map.of(
                          "term", Map.of("type", "string"),
                          "meaning", Map.of("type", "string")))));

  private final StoryWriterProperties props;
  private final JsonMapper json;
  private final String systemPrompt;
  private final Tool tool;

  public StoryRequests(StoryWriterProperties props, JsonMapper json) {
    this.props = props;
    this.json = json;
    this.systemPrompt = loadPrompt(props.promptVersion());
    Tool.InputSchema.Properties.Builder properties = Tool.InputSchema.Properties.builder();
    SCHEMA_PROPERTIES.forEach((k, v) -> properties.putAdditionalProperty(k, JsonValue.from(v)));
    this.tool =
        Tool.builder()
            .name(TOOL_NAME)
            .description("Return the story for one news item.")
            .inputSchema(
                Tool.InputSchema.builder()
                    .properties(properties.build())
                    .required(List.of("isAi", "reason"))
                    .build())
            .build();
  }

  public boolean modelConfigured() {
    return props.model() != null && !props.model().isBlank();
  }

  public String model() {
    return props.model();
  }

  public long maxTokens() {
    return props.maxOutputTokens();
  }

  public String systemPrompt() {
    return systemPrompt;
  }

  public Tool tool() {
    return tool;
  }

  public ToolChoice toolChoice() {
    return ToolChoice.ofTool(TOOL_NAME);
  }

  /** Only the allowed fields are serialised (spec 002 R1.2), plus retry feedback if any. */
  public String userText(StoryInput in, Optional<String> feedback) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("title", in.title());
    m.put("excerpt", in.excerpt());
    m.put("sourceName", in.sourceName());
    m.put("region", in.region());
    m.put("sectionHint", in.sectionHint());
    m.put("publishedAt", in.publishedAt() == null ? null : in.publishedAt().toString());
    String text = json.writeValueAsString(m);
    if (feedback.isPresent()) {
      text += "\n\nYour previous answer broke these rules: " + feedback.get() + ". Fix them.";
    }
    return text;
  }

  public StoryDraft draftFrom(Message message) {
    ToolUseBlock use =
        message.content().stream()
            .map(ContentBlock::toolUse)
            .flatMap(Optional::stream)
            .filter(t -> TOOL_NAME.equals(t.name()))
            .findFirst()
            .orElseThrow(() -> new WriterRejected("response had no write_story call", null));
    @SuppressWarnings("unchecked")
    Map<String, Object> out = use._input().convert(Map.class);

    List<KeyTerm> terms = new ArrayList<>();
    if (out.get("keyTerms") instanceof List<?> list) {
      for (Object o : list) {
        if (o instanceof Map<?, ?> m) {
          terms.add(new KeyTerm(str(m.get("term")), str(m.get("meaning"))));
        }
      }
    }
    return new StoryDraft(
        Boolean.TRUE.equals(out.get("isAi")),
        str(out.get("reason")),
        section(str(out.get("section"))),
        str(out.get("headline")),
        str(out.get("summary")),
        terms,
        message.model().asString(),
        (int) message.usage().inputTokens(),
        (int) message.usage().outputTokens());
  }

  private static Section section(String value) {
    if (value == null) {
      return null;
    }
    try {
      return Section.valueOf(value);
    } catch (IllegalArgumentException e) {
      return null; // the validator reports SHAPE
    }
  }

  private static String str(Object o) {
    return o == null ? null : o.toString();
  }

  static String loadPrompt(String version) {
    String path = "/prompts/story-writer." + version + ".md";
    try (InputStream in = StoryRequests.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException("missing prompt file " + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
