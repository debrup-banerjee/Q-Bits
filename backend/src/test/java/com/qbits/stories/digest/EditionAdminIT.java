package com.qbits.stories.digest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qbits.IntegrationTest;
import com.qbits.common.Ids;
import com.qbits.ingestion.domain.Item;
import com.qbits.ingestion.domain.StoryStatus;
import com.qbits.ingestion.persistence.ItemRepository;
import com.qbits.stories.BatchStoryWriter;
import com.qbits.stories.FakeBatchStoryWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class EditionAdminIT extends IntegrationTest {

  @Autowired EditionJob job;
  @Autowired EditionRepository editions;
  @Autowired ItemRepository items;
  @Autowired BatchStoryWriter batches;
  @Autowired LockProvider lockProvider;

  @Test
  void cutsAndSubmitsAnEditionOnDemand() { // 006 R6.3
    ((FakeBatchStoryWriter) batches).autoFinish = false;
    Instant now = Instant.now();
    UUID item = Ids.newId();
    items.insertIfNew(
        new Item(
            item,
            "src",
            "https://a.example/" + item,
            "Lab shares AI tool",
            "Teaser.",
            item.toString(),
            now.minus(Duration.ofHours(1)),
            false,
            now.minus(Duration.ofMinutes(50)),
            1.0,
            StoryStatus.PENDING,
            false));

    UUID id = new EditionAdminCommands(job).runNow();

    Edition e = editions.find(id).orElseThrow();
    assertThat(e.status()).isEqualTo(EditionStatus.SUBMITTED);
    assertThat(e.submitted()).isEqualTo(1);
    assertThatThrownBy(() -> new EditionAdminCommands(job).runNow())
        .hasMessageContaining("already in progress");
  }

  @Test
  void waitsForTheScheduledTicksLock() { // 006 R6.3 (safe to run twice)
    SimpleLock held =
        lockProvider
            .lock(
                new LockConfiguration(
                    Instant.now(), "digest", Duration.ofMinutes(5), Duration.ZERO))
            .orElseThrow();
    try {
      assertThatThrownBy(() -> new EditionAdminCommands(job).runNow())
          .hasMessageContaining("edition job is running");
      assertThat(editions.findOpen()).isEmpty();
    } finally {
      held.unlock();
    }
  }
}
