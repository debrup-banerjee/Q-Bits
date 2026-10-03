package com.qbits.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.qbits.IntegrationTest;
import com.qbits.common.Ids;
import com.qbits.ingestion.domain.Item;
import com.qbits.ingestion.domain.StoryStatus;
import com.qbits.ingestion.persistence.ItemRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RetentionJobIT extends IntegrationTest {

  private static final Instant NOW = Instant.parse("2026-10-10T02:15:00Z");

  @Autowired ItemRepository items;
  @Autowired IngestionProperties props;
  @Autowired LockingTaskExecutor locks;

  @Test
  void deletesItemsPublishedMoreThanSevenDaysAgo() { // 001 R9.2
    Item old = item("https://a.example/old", NOW.minus(Duration.ofDays(7)).minusSeconds(1));
    Item edge = item("https://a.example/edge", NOW.minus(Duration.ofDays(7)));
    Item fresh = item("https://a.example/fresh", NOW.minus(Duration.ofDays(1)));
    items.insertIfNew(old);
    items.insertIfNew(edge);
    items.insertIfNew(fresh);

    new RetentionJob(items, props, locks, Clock.fixed(NOW, ZoneOffset.UTC)).runOnce();

    assertThat(items.findById(old.id())).isEmpty();
    assertThat(items.findById(edge.id())).isPresent();
    assertThat(items.findById(fresh.id())).isPresent();
  }

  private static Item item(String url, Instant published) {
    return new Item(
        Ids.newId(),
        "src",
        url,
        "T",
        null,
        url,
        published,
        false,
        published,
        1.0,
        StoryStatus.PENDING,
        false);
  }
}
