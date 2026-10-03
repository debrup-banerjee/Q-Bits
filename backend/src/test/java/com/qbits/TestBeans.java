package com.qbits;

import com.qbits.stories.FakeStoryWriter;
import com.qbits.stories.StoryFixtures;
import com.qbits.stories.StoryWriter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Beans that replace external services in tests. No test calls the real summary service. */
@TestConfiguration
public class TestBeans {

  @Bean
  @Primary
  StoryWriter fakeStoryWriter() {
    return new FakeStoryWriter(StoryFixtures::validDraft);
  }
}
