package com.qbits.stories;

import com.qbits.stories.domain.GroupCandidate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Scripted same-event grouping for tests; finds no repeats unless told otherwise. */
public class FakeStoryGrouper implements StoryGrouper {

  private Function<List<GroupCandidate>, List<Set<UUID>>> answer = c -> List.of();
  private RuntimeException failure;
  private final List<List<GroupCandidate>> calls = new ArrayList<>();

  public FakeStoryGrouper answer(Function<List<GroupCandidate>, List<Set<UUID>>> answer) {
    this.answer = answer;
    this.failure = null;
    return this;
  }

  /** Every call throws this until {@link #answer} is set again. */
  public FakeStoryGrouper failWith(RuntimeException failure) {
    this.failure = failure;
    return this;
  }

  /** The candidates of each call, in order. */
  public List<List<GroupCandidate>> calls() {
    return calls;
  }

  @Override
  public List<Set<UUID>> group(List<GroupCandidate> candidates) {
    calls.add(List.copyOf(candidates));
    if (failure != null) {
      throw failure;
    }
    return answer.apply(candidates);
  }
}
