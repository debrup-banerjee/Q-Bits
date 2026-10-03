package com.qbits.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.qbits.IntegrationTest;
import com.qbits.common.Ids;
import com.qbits.ingestion.domain.Item;
import com.qbits.ingestion.domain.StoryStatus;
import com.qbits.ingestion.persistence.ItemRepository;
import com.qbits.sources.SourceAdminCommands;
import com.qbits.sources.SourceRegistry;
import com.qbits.sources.domain.Region;
import com.qbits.sources.domain.Source;
import com.qbits.sources.domain.SourceType;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

class SourceAdminIT extends IntegrationTest {

  private static final Instant NOW = Instant.parse("2026-10-03T06:00:00Z");

  @Autowired ItemRepository items;
  @Autowired ApplicationContext context;

  @Test
  void disablingASourceHidesItsItemsAndReEnablingShowsThem() { // 001 R8.1
    Item kept = item("on-src", "https://a.example/1");
    Item hidden = item("off-src", "https://b.example/1");
    items.insertIfNew(kept);
    items.insertIfNew(hidden);

    new SourceVisibility(registry(true, false), items).sync();

    assertThat(items.findById(kept.id()).orElseThrow().hidden()).isFalse();
    assertThat(items.findById(hidden.id()).orElseThrow().hidden()).isTrue();

    new SourceVisibility(registry(true, true), items).sync();

    assertThat(items.findById(hidden.id()).orElseThrow().hidden()).isFalse();
  }

  @Test
  void itemsOfSourcesRemovedFromTheFileAreHidden() { // 001 R8.1
    Item orphan = item("removed-src", "https://c.example/1");
    items.insertIfNew(orphan);

    new SourceVisibility(registry(true, true), items).sync();

    assertThat(items.findById(orphan.id()).orElseThrow().hidden()).isTrue();
  }

  @Test
  void purgeDeletesEveryItemOfTheSource() { // 001 R8.2
    items.insertIfNew(item("off-src", "https://b.example/1"));
    items.insertIfNew(item("off-src", "https://b.example/2"));
    items.insertIfNew(item("on-src", "https://a.example/1"));

    int deleted = new SourceAdminCommands(items, context).purge("off-src");

    assertThat(deleted).isEqualTo(2);
    assertThat(items.countBySource("off-src")).isZero();
    assertThat(items.countBySource("on-src")).isEqualTo(1);
  }

  private static SourceRegistry registry(boolean onEnabled, boolean offEnabled) {
    return new SourceRegistry(List.of(source("on-src", onEnabled), source("off-src", offEnabled)));
  }

  private static Source source(String id, boolean enabled) {
    URI uri = URI.create("https://" + id + ".example/feed");
    return new Source(
        id, id, uri, uri, SourceType.RSS, uri, null, enabled, true, Region.GLOBAL, null, null);
  }

  private static Item item(String sourceId, String url) {
    return new Item(
        Ids.newId(),
        sourceId,
        url,
        "T",
        null,
        url,
        NOW,
        false,
        NOW,
        1.0,
        StoryStatus.PENDING,
        false);
  }
}
