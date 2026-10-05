package com.qbits.stories;

import com.qbits.ingestion.domain.Item;
import com.qbits.sources.SourceRegistry;
import com.qbits.sources.domain.Source;
import com.qbits.stories.domain.StoryInput;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Builds the allowed summary-service input for an item (spec 002 R1.2). */
@Component
public class StoryInputs {

  private final SourceRegistry registry;

  public StoryInputs(SourceRegistry registry) {
    this.registry = registry;
  }

  public StoryInput of(Item item) {
    Optional<Source> source = registry.find(item.sourceId());
    return new StoryInput(
        item.sourceTitle(),
        item.sourceExcerpt(),
        source.map(Source::name).orElse(item.sourceId()),
        source.map(s -> s.region().name()).orElse("GLOBAL"),
        source.flatMap(Source::hint).orElse(null),
        item.publishedAt());
  }
}
