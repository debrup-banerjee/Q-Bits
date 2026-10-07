package com.qbits.images.anthropic;

import com.anthropic.client.AnthropicClient;
import com.qbits.images.ImageQueryWriter;
import com.qbits.images.ImagesProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/** Wires the model-written photo search phrase when the Anthropic provider is in use. */
@Configuration
@ConditionalOnProperty(name = "qbits.story-writer.provider", havingValue = "anthropic")
class ImageQueryConfig {

  @Bean
  ImageQueryWriter anthropicImageQueryWriter(
      AnthropicClient client, ImagesProperties props, JsonMapper json) {
    return new AnthropicImageQueryWriter(client, props, json);
  }
}
