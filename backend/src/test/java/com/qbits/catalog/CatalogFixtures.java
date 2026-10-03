package com.qbits.catalog;

import com.qbits.common.Ids;
import com.qbits.ingestion.domain.Item;
import com.qbits.ingestion.domain.StoryStatus;
import com.qbits.ingestion.persistence.ItemRepository;
import com.qbits.stories.domain.KeyTerm;
import com.qbits.stories.domain.Section;
import com.qbits.stories.domain.Story;
import com.qbits.stories.persistence.StoryRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Seeds items and stories for catalog tests. */
public final class CatalogFixtures {

  private final ItemRepository items;
  private final StoryRepository stories;

  public CatalogFixtures(ItemRepository items, StoryRepository stories) {
    this.items = items;
    this.stories = stories;
  }

  public UUID published(String sourceId, Section section, Instant publishedAt) {
    UUID id = Ids.newId();
    items.insertIfNew(
        new Item(
            id,
            sourceId,
            "https://news.example/" + id,
            "SOURCE TITLE",
            "SOURCE EXCERPT",
            id.toString(),
            publishedAt,
            false,
            publishedAt,
            1.0,
            StoryStatus.PENDING,
            false));
    stories.save(
        new Story(
            id,
            section,
            "Headline " + id,
            "Our summary.",
            List.of(new KeyTerm("GPU", "A chip for many small sums.")),
            "m",
            "v1",
            publishedAt,
            1,
            1,
            1));
    items.updateStoryStatus(id, StoryStatus.PUBLISHED, null);
    return id;
  }

  public UUID withStatus(String sourceId, StoryStatus status, Instant publishedAt) {
    UUID id = Ids.newId();
    items.insertIfNew(
        new Item(
            id,
            sourceId,
            "https://news.example/" + id,
            "T",
            "E",
            id.toString(),
            publishedAt,
            false,
            publishedAt,
            1.0,
            StoryStatus.PENDING,
            false));
    items.updateStoryStatus(id, status, null);
    return id;
  }
}
