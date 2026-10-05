package com.qbits.stories.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.qbits.stories.BatchStoryWriter;
import com.qbits.stories.StoryWriter;
import com.qbits.stories.StoryWriterProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wires the Anthropic writer. Locally, ANTHROPIC_API_KEY authenticates directly. In the cloud,
 * leave it unset and set ANTHROPIC_FEDERATION_RULE_ID, ANTHROPIC_ORGANIZATION_ID,
 * ANTHROPIC_IDENTITY_TOKEN_FILE (or ANTHROPIC_IDENTITY_TOKEN) and ANTHROPIC_SERVICE_ACCOUNT_ID
 * instead; the SDK then exchanges the workload's identity token for a short-lived access token (AWS
 * workload identity federation) and no static key is ever stored. Without either, the app still
 * runs: ingestion continues and items wait as PENDING.
 */
@Configuration
@ConditionalOnProperty(name = "qbits.story-writer.provider", havingValue = "anthropic")
class AnthropicConfig {

  private static final Logger log = LoggerFactory.getLogger(AnthropicConfig.class);

  @Bean(destroyMethod = "close")
  AnthropicClient anthropicClient(
      StoryWriterProperties props, @Value("${qbits.story-writer.base-url:}") String baseUrl) {
    String key = System.getenv("ANTHROPIC_API_KEY");
    String federationRuleId = System.getenv("ANTHROPIC_FEDERATION_RULE_ID");
    boolean hasKey = key != null && !key.isBlank();
    boolean hasFederation = federationRuleId != null && !federationRuleId.isBlank();

    AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder();
    if (hasKey || hasFederation) {
      // fromEnv() picks ANTHROPIC_API_KEY when set; otherwise it resolves workload identity
      // federation from ANTHROPIC_FEDERATION_RULE_ID, ANTHROPIC_ORGANIZATION_ID,
      // ANTHROPIC_IDENTITY_TOKEN_FILE/ANTHROPIC_IDENTITY_TOKEN and ANTHROPIC_SERVICE_ACCOUNT_ID.
      builder.fromEnv();
    } else {
      log.warn(
          "Neither ANTHROPIC_API_KEY nor ANTHROPIC_FEDERATION_RULE_ID is set; stories will stay"
              + " pending until one is");
      builder.apiKey("missing");
    }
    builder
        .timeout(props.timeout())
        .maxRetries(0); // the story job backs off and retries (spec 002 R9.2)
    if (!baseUrl.isBlank()) {
      builder.baseUrl(baseUrl);
    }
    return builder.build();
  }

  @Bean
  StoryRequests storyRequests(StoryWriterProperties props, JsonMapper json) {
    return new StoryRequests(props, json);
  }

  @Bean
  StoryWriter anthropicStoryWriter(AnthropicClient client, StoryRequests requests) {
    return new AnthropicStoryWriter(client, requests);
  }

  @Bean
  BatchStoryWriter anthropicBatchStoryWriter(AnthropicClient client, StoryRequests requests) {
    return new AnthropicBatchStoryWriter(client, requests);
  }
}
