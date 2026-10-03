package com.qbits.stories;

import com.qbits.stories.domain.StoryDraft;
import com.qbits.stories.domain.StoryInput;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Scripted writer for tests: returns queued responses, or a default built from the input. */
public class FakeStoryWriter implements StoryWriter {

  private final Deque<Function<StoryInput, StoryDraft>> script = new ArrayDeque<>();
  private final Function<StoryInput, StoryDraft> fallback;
  public final List<StoryInput> inputs = new ArrayList<>();
  public final List<Optional<String>> feedback = new ArrayList<>();

  public FakeStoryWriter(Function<StoryInput, StoryDraft> fallback) {
    this.fallback = fallback;
  }

  public FakeStoryWriter then(Function<StoryInput, StoryDraft> response) {
    script.add(response);
    return this;
  }

  public FakeStoryWriter thenUnavailable() {
    script.add(
        in -> {
          throw new WriterUnavailable("service down", null);
        });
    return this;
  }

  public FakeStoryWriter thenRejected() {
    script.add(
        in -> {
          throw new WriterRejected("bad request", null);
        });
    return this;
  }

  @Override
  public synchronized StoryDraft write(StoryInput input, Optional<String> fb) {
    inputs.add(input);
    feedback.add(fb);
    Function<StoryInput, StoryDraft> next = script.isEmpty() ? fallback : script.poll();
    return next.apply(input);
  }
}
