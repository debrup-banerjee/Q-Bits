package com.qbits.stories.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicRetryableException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.UnprocessableEntityException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolChoice;
import com.anthropic.models.messages.ToolUseBlock;
import com.qbits.stories.StoryWriter;
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
 * Writes stories with the Anthropic Messages API. One call per attempt, with a forced tool call so
 * the answer is always JSON matching {@link #SCHEMA_PROPERTIES} (spec 002 design).
 */
public class AnthropicStoryWriter implements StoryWriter {

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
                  List.of("GLOBAL_AI_TECH", "WORLD_BUSINESS", "INDIA_AI", "INNOVATIONS_RESEARCH")),
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
                      "type", "object",
                      "required", List.of("term", "meaning"),
                      "properties",
                          Map.of(
                              "term", Map.of("type", "string"),
                              "meaning", Map.of("type", "string")))));

  private final AnthropicClient client;
  private final StoryWriterProperties props;
  private final JsonMapper json;
  private final String systemPrompt;
  private final Tool tool;

  public AnthropicStoryWriter(
      AnthropicClient client, StoryWriterProperties props, JsonMapper json) {
    this.client = client;
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

  @Override
  public StoryDraft write(StoryInput input, Optional<String> feedback) {
    if (props.model() == null || props.model().isBlank()) {
      throw new WriterUnavailable("no model configured; set QBITS_STORY_MODEL", null);
    }
    String user = toJson(input);
    if (feedback.isPresent()) {
      user += "\n\nYour previous answer broke these rules: " + feedback.get() + ". Fix them.";
    }
    MessageCreateParams params =
        MessageCreateParams.builder()
            .model(props.model())
            .maxTokens(props.maxOutputTokens())
            .system(systemPrompt)
            .addUserMessage(user)
            .addTool(tool)
            .toolChoice(ToolChoice.ofTool(TOOL_NAME))
            .build();

    Message message;
    try {
      message = client.messages().create(params);
    } catch (BadRequestException | UnprocessableEntityException e) {
      throw new WriterRejected("HTTP " + e.statusCode(), e);
    } catch (AnthropicServiceException e) {
      // 401/403/404 are configuration problems; 429/5xx are temporary. Either way, try later.
      throw new WriterUnavailable("HTTP " + e.statusCode(), e);
    } catch (AnthropicIoException | AnthropicRetryableException e) {
      throw new WriterUnavailable("network: " + e.getClass().getSimpleName(), e);
    }
    return toDraft(message);
  }

  private StoryDraft toDraft(Message message) {
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

  /** Only the allowed fields are serialised (spec 002 R1.2). */
  private String toJson(StoryInput in) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("title", in.title());
    m.put("excerpt", in.excerpt());
    m.put("sourceName", in.sourceName());
    m.put("region", in.region());
    m.put("sectionHint", in.sectionHint());
    m.put("publishedAt", in.publishedAt() == null ? null : in.publishedAt().toString());
    return json.writeValueAsString(m);
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
    try (InputStream in = AnthropicStoryWriter.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException("missing prompt file " + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
