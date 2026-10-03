package com.qbits.stories.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
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
 * Wires the Anthropic writer. The API key comes only from the ANTHROPIC_API_KEY environment
 * variable. Without it the app still runs: ingestion continues and items wait as PENDING.
 */
@Configuration
@ConditionalOnProperty(name = "qbits.story-writer.provider", havingValue = "anthropic")
class AnthropicConfig {

  private static final Logger log = LoggerFactory.getLogger(AnthropicConfig.class);

  @Bean(destroyMethod = "close")
  AnthropicClient anthropicClient(
      StoryWriterProperties props, @Value("${qbits.story-writer.base-url:}") String baseUrl) {
    String key = System.getenv("ANTHROPIC_API_KEY");
    if (key == null || key.isBlank()) {
      log.warn("ANTHROPIC_API_KEY is not set; stories will stay pending until it is");
    }
    AnthropicOkHttpClient.Builder builder =
        AnthropicOkHttpClient.builder()
            .apiKey(key == null || key.isBlank() ? "missing" : key)
            .timeout(props.timeout())
            .maxRetries(0); // the story job backs off and retries (spec 002 R9.2)
    if (!baseUrl.isBlank()) {
      builder.baseUrl(baseUrl);
    }
    return builder.build();
  }

  @Bean
  StoryWriter anthropicStoryWriter(
      AnthropicClient client, StoryWriterProperties props, JsonMapper json) {
    return new AnthropicStoryWriter(client, props, json);
  }
}
