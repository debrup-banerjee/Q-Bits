package com.qbits.stories;

import com.qbits.stories.domain.KeyTerm;
import com.qbits.stories.domain.Section;
import com.qbits.stories.domain.StoryDraft;
import com.qbits.stories.domain.StoryInput;
import java.time.Duration;
import java.util.List;

/** Shared test data for story tests. */
public final class StoryFixtures {

  private StoryFixtures() {}

  public static StoryWriterProperties props() {
    return new StoryWriterProperties(
        "anthropic",
        "test-model",
        "v1",
        800,
        500,
        4,
        20,
        Duration.ofSeconds(30),
        Duration.ofMinutes(2),
        Duration.ofMinutes(30));
  }

  /** A draft that passes every validation rule for any input with a short excerpt. */
  public static StoryDraft validDraft(StoryInput in) {
    String summary =
        "Here is a simple look at a new development in artificial intelligence, according to "
            + in.sourceName()
            + ". A team has shared work that could change how people use smart software every day."
            + " The idea is to make tools that understand questions better and answer them more"
            + " clearly. Why does it matter? Better tools can save time for students, doctors and"
            + " office workers alike. Keep an eye out for more details soon.";
    return new StoryDraft(
        true,
        "AI story",
        Section.GLOBAL_AI_TECH,
        "A new step for smart software",
        summary,
        List.of(
            new KeyTerm("artificial intelligence", "Computer systems that can learn and decide.")),
        "test-model",
        100,
        200);
  }

  public static StoryDraft invalidDraft(StoryInput in) {
    return new StoryDraft(
        true,
        "AI",
        Section.GLOBAL_AI_TECH,
        "Too short",
        "Tiny summary.",
        List.of(),
        "test-model",
        10,
        10);
  }

  public static StoryDraft notAi(StoryInput in) {
    return new StoryDraft(
        false, "This is about football", null, null, null, null, "test-model", 10, 5);
  }
}
