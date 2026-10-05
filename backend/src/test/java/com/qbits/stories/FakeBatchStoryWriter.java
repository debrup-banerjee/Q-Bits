package com.qbits.stories;

import com.qbits.stories.domain.StoryDraft;
import com.qbits.stories.domain.StoryInput;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Scripted batch writer for tests. Each submitted request is answered by {@code answer}; a batch
 * stays IN_PROGRESS until {@link #finish} is called (or {@code autoFinish} is true).
 */
public class FakeBatchStoryWriter implements BatchStoryWriter {

  public final List<List<BatchRequest>> submitted = new ArrayList<>();
  public Function<BatchRequest, BatchOutcome> answer;
  public boolean autoFinish = true;
  public RuntimeException failNextSubmit;
  public RuntimeException failNextResults;
  private final Map<String, Boolean> ended = new HashMap<>();
  private final Map<String, List<BatchRequest>> byId = new HashMap<>();

  public FakeBatchStoryWriter(Function<StoryInput, StoryDraft> drafts) {
    this.answer = r -> new BatchOutcome.Succeeded(r.customId(), drafts.apply(r.input()));
  }

  @Override
  public synchronized String submit(List<BatchRequest> requests) {
    if (failNextSubmit != null) {
      RuntimeException e = failNextSubmit;
      failNextSubmit = null;
      throw e;
    }
    String id = "batch_" + (submitted.size() + 1);
    submitted.add(List.copyOf(requests));
    byId.put(id, List.copyOf(requests));
    ended.put(id, autoFinish);
    return id;
  }

  public synchronized void finish(String batchId) {
    ended.put(batchId, true);
  }

  @Override
  public synchronized BatchState status(String batchId) {
    return Boolean.TRUE.equals(ended.get(batchId)) ? BatchState.ENDED : BatchState.IN_PROGRESS;
  }

  @Override
  public synchronized List<BatchOutcome> results(String batchId) {
    if (failNextResults != null) {
      RuntimeException e = failNextResults;
      failNextResults = null;
      throw e;
    }
    return byId.get(batchId).stream().map(answer).toList();
  }
}
