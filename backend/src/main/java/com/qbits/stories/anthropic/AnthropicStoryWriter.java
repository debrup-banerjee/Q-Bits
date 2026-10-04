package com.qbits.stories.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicRetryableException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.UnprocessableEntityException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.qbits.stories.StoryWriter;
import com.qbits.stories.StoryWriterProperties;
import com.qbits.stories.domain.StoryDraft;
import com.qbits.stories.domain.StoryInput;
import java.util.Optional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes one story with the Anthropic Messages API, forcing the {@code write_story} tool so the
 * answer is always schema-shaped JSON (spec 002). Used in realtime mode, for operator rewrites and
 * for the live sample check.
 */
public class AnthropicStoryWriter implements StoryWriter {

  private final AnthropicClient client;
  private final StoryRequests requests;

  public AnthropicStoryWriter(
      AnthropicClient client, StoryWriterProperties props, JsonMapper json) {
    this(client, new StoryRequests(props, json));
  }

  public AnthropicStoryWriter(AnthropicClient client, StoryRequests requests) {
    this.client = client;
    this.requests = requests;
  }

  @Override
  public StoryDraft write(StoryInput input, Optional<String> feedback) {
    if (!requests.modelConfigured()) {
      throw new WriterUnavailable("no model configured; set QBITS_STORY_MODEL", null);
    }
    MessageCreateParams params =
        MessageCreateParams.builder()
            .model(requests.model())
            .maxTokens(requests.maxTokens())
            .system(requests.systemPrompt())
            .addUserMessage(requests.userText(input, feedback))
            .addTool(requests.tool())
            .toolChoice(requests.toolChoice())
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
    } catch (AnthropicException e) {
      throw new WriterUnavailable("unreadable response: " + e.getClass().getSimpleName(), e);
    }
    return requests.draftFrom(message);
  }
}
