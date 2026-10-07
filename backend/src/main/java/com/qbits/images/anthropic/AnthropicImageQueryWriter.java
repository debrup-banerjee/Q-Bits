package com.qbits.images.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.RequestOptions;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolChoice;
import com.anthropic.models.messages.ToolUseBlock;
import com.qbits.images.ImageQueryWriter;
import com.qbits.images.ImagesProperties;
import com.qbits.stories.domain.KeyTerm;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * Asks the model for a photo search phrase with one small call, forcing the {@code photo_query}
 * tool (spec 009 R2.1). Any failure returns empty: a missing phrase only means the fallback is
 * used, so this never retries or throws.
 */
public class AnthropicImageQueryWriter implements ImageQueryWriter {

  private static final Logger log = LoggerFactory.getLogger(AnthropicImageQueryWriter.class);
  static final String TOOL_NAME = "photo_query";

  private final AnthropicClient client;
  private final ImagesProperties.Query props;
  private final JsonMapper json;
  private final String systemPrompt;
  private final Tool tool;

  public AnthropicImageQueryWriter(
      AnthropicClient client, ImagesProperties props, JsonMapper json) {
    this.client = client;
    this.props = props.query();
    this.json = json;
    this.systemPrompt = loadPrompt(this.props.promptVersion());
    this.tool =
        Tool.builder()
            .name(TOOL_NAME)
            .description("Return a short stock-photo search phrase for the story.")
            .inputSchema(
                Tool.InputSchema.builder()
                    .properties(
                        Tool.InputSchema.Properties.builder()
                            .putAdditionalProperty(
                                "phrase", JsonValue.from(Map.of("type", "string", "maxLength", 60)))
                            .build())
                    .required(List.of("phrase"))
                    .build())
            .build();
  }

  @Override
  public Optional<String> phraseFor(String headline, String summary, List<KeyTerm> keyTerms) {
    if (!props.modelConfigured()) {
      return Optional.empty();
    }
    Map<String, Object> in = new LinkedHashMap<>();
    in.put("headline", headline);
    in.put("summary", summary);
    in.put("keyTerms", keyTerms.stream().map(KeyTerm::term).toList());
    MessageCreateParams params =
        MessageCreateParams.builder()
            .model(props.model())
            .maxTokens(props.maxOutputTokens())
            .system(systemPrompt)
            .addUserMessage(json.writeValueAsString(in))
            .addTool(tool)
            .toolChoice(ToolChoice.ofTool(TOOL_NAME))
            .build();
    try {
      Message message =
          client
              .messages()
              .create(params, RequestOptions.builder().timeout(props.timeout()).build());
      return message.content().stream()
          .map(ContentBlock::toolUse)
          .flatMap(Optional::stream)
          .filter(t -> TOOL_NAME.equals(t.name()))
          .findFirst()
          .map(AnthropicImageQueryWriter::phrase);
    } catch (RuntimeException e) {
      log.warn("photo query call failed error={}", e.getClass().getSimpleName());
      return Optional.empty();
    }
  }

  private static String phrase(ToolUseBlock use) {
    @SuppressWarnings("unchecked")
    Map<String, Object> out = use._input().convert(Map.class);
    Object p = out.get("phrase");
    return p == null ? null : p.toString();
  }

  static String loadPrompt(String version) {
    String path = "/prompts/image-query." + version + ".md";
    try (InputStream in = AnthropicImageQueryWriter.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException("missing prompt file " + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
