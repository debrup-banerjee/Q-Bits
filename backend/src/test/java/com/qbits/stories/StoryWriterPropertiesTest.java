package com.qbits.stories;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

class StoryWriterPropertiesTest {

  @Configuration
  @EnableConfigurationProperties(StoryWriterProperties.class)
  static class Config {}

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
          .withUserConfiguration(Config.class)
          .withPropertyValues(
              "qbits.story-writer.provider=anthropic",
              "qbits.story-writer.prompt-version=v2",
              "qbits.story-writer.max-output-tokens=800",
              "qbits.story-writer.daily-call-cap=500",
              "qbits.story-writer.parallelism=4",
              "qbits.story-writer.batch-size=20",
              "qbits.story-writer.timeout=PT30S",
              "qbits.story-writer.tick=PT2M",
              "qbits.story-writer.max-backoff=PT30M");

  @Test
  void readsBothModes() { // 006 R6.4
    runner
        .withPropertyValues("qbits.story-writer.mode=digest")
        .run(c -> assertThat(c.getBean(StoryWriterProperties.class).realtime()).isFalse());
    runner
        .withPropertyValues("qbits.story-writer.mode=realtime")
        .run(c -> assertThat(c.getBean(StoryWriterProperties.class).realtime()).isTrue());
  }

  @Test
  void anUnknownModeStopsStartup() { // 006 R6.4
    runner
        .withPropertyValues("qbits.story-writer.mode=real-time-ish")
        .run(c -> assertThat(c).hasFailed());
  }

  @Test
  void aMissingModeStopsStartup() { // 006 R6.4
    runner.run(c -> assertThat(c).hasFailed());
  }
}
