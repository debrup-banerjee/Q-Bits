package com.qbits.ingestion;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.qbits.resources.ResourceCheckJob;
import com.qbits.stories.StoryJob;
import com.qbits.stories.StoryWriterProperties;
import com.qbits.stories.digest.EditionJob;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class SchedulingModeTest {

  private final StoryJob stories = mock(StoryJob.class);
  private final EditionJob editions = mock(EditionJob.class);

  private SchedulingConfig.Triggers triggers(StoryWriterProperties.Mode mode) {
    return new SchedulingConfig.Triggers(
        mock(IngestionJob.class),
        mock(RetentionJob.class),
        stories,
        mock(ResourceCheckJob.class),
        editions,
        new StoryWriterProperties(
            mode,
            "anthropic",
            "m",
            "v2",
            800,
            500,
            4,
            20,
            Duration.ofSeconds(30),
            Duration.ofMinutes(2),
            Duration.ofMinutes(30)));
  }

  @Test
  void digestModeRunsOnlyTheDailyEdition() { // 006 R6.4
    SchedulingConfig.Triggers t = triggers(StoryWriterProperties.Mode.DIGEST);
    t.runDigest();
    t.writeStories();
    verify(editions).runOnce();
    verify(stories, never()).runOnce();
  }

  @Test
  void realtimeModeRunsOnlyOneAtATimeWriting() { // 006 R6.4
    SchedulingConfig.Triggers t = triggers(StoryWriterProperties.Mode.REALTIME);
    t.runDigest();
    t.writeStories();
    verify(stories).runOnce();
    verify(editions, never()).runOnce();
  }
}
