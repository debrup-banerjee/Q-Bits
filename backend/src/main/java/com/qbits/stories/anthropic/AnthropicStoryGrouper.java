package com.qbits.stories.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.RequestOptions;
import com.anthropic.errors.AnthropicException;
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
import com.qbits.stories.DedupeProperties;
import com.qbits.stories.StoryGrouper;
import com.qbits.stories.StoryWriter.WriterRejected;
import com.qbits.stories.StoryWriter.WriterUnavailable;
import com.qbits.stories.StoryWriterProperties;
import com.qbits.stories.domain.GroupCandidate;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Groups same-event stories with one Messages call, forcing the {@code group_stories} tool (spec
 * 008). Stories are numbered in the request so the model never has to copy ids; numbers it invents
 * map to an id no candidate has, which {@code DuplicateRules} then drops (R1.4).
 */
public class AnthropicStoryGrouper implements StoryGrouper {

  static final String TOOL_NAME = "group_stories";

  /** Stands in for a number outside the list; never a candidate id. */
  static final UUID UNKNOWN = new UUID(0, 0);

  private final AnthropicClient client;
  private final StoryWriterProperties writer;
  private final DedupeProperties props;
  private final String systemPrompt;
  private final Tool tool;

  public AnthropicStoryGrouper(
      AnthropicClient client, StoryWriterProperties writer, DedupeProperties props) {
    this.client = client;
    this.writer = writer;
    this.props = props;
    this.systemPrompt = loadPrompt(props.promptVersion());
    this.tool =
        Tool.builder()
            .name(TOOL_NAME)
            .description("Return the groups of stories that report the same event.")
            .inputSchema(
                Tool.InputSchema.builder()
                    .properties(
                        Tool.InputSchema.Properties.builder()
                            .putAdditionalProperty(
                                "groups",
                                JsonValue.from(
                                    Map.of(
                                        "type",
                                        "array",
                                        "items",
                                        Map.of(
                                            "type",
                                            "array",
                                            "minItems",
                                            2,
                                            "items",
                                            Map.of("type", "integer")))))
                            .build())
                    .required(List.of("groups"))
                    .build())
            .build();
  }

  @Override
  public List<Set<UUID>> group(List<GroupCandidate> candidates) {
    if (writer.model() == null || writer.model().isBlank()) {
      throw new WriterUnavailable("no model configured; set QBITS_STORY_MODEL", null);
    }
    if (candidates.size() < 2) {
      return List.of();
    }
    MessageCreateParams params =
        MessageCreateParams.builder()
            .model(writer.model())
            .maxTokens(props.maxOutputTokens())
            .system(systemPrompt)
            .addUserMessage(userText(candidates))
            .addTool(tool)
            .toolChoice(ToolChoice.ofTool(TOOL_NAME))
            .build();
    Message message;
    try {
      message =
          client
              .messages()
              .create(params, RequestOptions.builder().timeout(props.timeout()).build());
    } catch (BadRequestException | UnprocessableEntityException e) {
      throw new WriterRejected("HTTP " + e.statusCode(), e);
    } catch (AnthropicServiceException e) {
      throw new WriterUnavailable("HTTP " + e.statusCode(), e);
    } catch (AnthropicIoException | AnthropicRetryableException e) {
      throw new WriterUnavailable("network: " + e.getClass().getSimpleName(), e);
    } catch (AnthropicException e) {
      throw new WriterUnavailable("unreadable response: " + e.getClass().getSimpleName(), e);
    }
    return groupsFrom(message, candidates);
  }

  /** One numbered entry per story, our own text only (R1.3). */
  static String userText(List<GroupCandidate> candidates) {
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < candidates.size(); i++) {
      GroupCandidate c = candidates.get(i);
      text.append('[')
          .append(i + 1)
          .append("] section=")
          .append(c.section().displayName())
          .append(" source=")
          .append(c.sourceName())
          .append(" published=")
          .append(c.publishedAt().truncatedTo(ChronoUnit.MINUTES))
          .append("\n    headline: ")
          .append(oneLine(c.headline()))
          .append("\n    summary: ")
          .append(oneLine(c.summary()))
          .append("\n\n");
    }
    return text.toString().strip();
  }

  /** Maps the tool's story numbers back to ids. The tool missing means a retry later. */
  static List<Set<UUID>> groupsFrom(Message message, List<GroupCandidate> candidates) {
    ToolUseBlock use =
        message.content().stream()
            .map(ContentBlock::toolUse)
            .flatMap(Optional::stream)
            .filter(t -> TOOL_NAME.equals(t.name()))
            .findFirst()
            .orElseThrow(() -> new WriterUnavailable("response had no group_stories call", null));
    Object raw = use._input().convert(Map.class).get("groups");
    if (!(raw instanceof List<?> groups)) {
      throw new WriterUnavailable("group_stories had no groups list", null);
    }
    List<Set<UUID>> out = new ArrayList<>();
    for (Object g : groups) {
      if (!(g instanceof List<?> numbers)) {
        continue;
      }
      Set<UUID> ids = new LinkedHashSet<>();
      for (Object n : numbers) {
        int index = n instanceof Number num ? num.intValue() - 1 : -1;
        ids.add(index >= 0 && index < candidates.size() ? candidates.get(index).id() : UNKNOWN);
      }
      out.add(ids);
    }
    return out;
  }

  private static String oneLine(String value) {
    return value == null ? "" : value.strip().replaceAll("\\s+", " ");
  }

  static String loadPrompt(String version) {
    String path = "/prompts/story-grouper." + version + ".md";
    try (InputStream in = AnthropicStoryGrouper.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException("missing prompt file " + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
