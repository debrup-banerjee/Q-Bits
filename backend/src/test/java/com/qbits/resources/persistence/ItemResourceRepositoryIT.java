package com.qbits.resources.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.qbits.IntegrationTest;
import com.qbits.common.Ids;
import com.qbits.ingestion.domain.Item;
import com.qbits.ingestion.domain.StoryStatus;
import com.qbits.ingestion.persistence.ItemRepository;
import com.qbits.resources.domain.Candidate;
import com.qbits.resources.domain.ItemResource;
import com.qbits.resources.domain.LinkOrigin;
import com.qbits.resources.domain.ResourceHost;
import com.qbits.resources.domain.ResourceStatus;
import com.qbits.resources.domain.ResourceType;
import com.qbits.resources.persistence.LinkCheckCacheRepository.CachedCheck;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ItemResourceRepositoryIT extends IntegrationTest {

  private static final Instant NOW = Instant.parse("2026-10-03T06:00:00Z");
  private static final Candidate WHISPER =
      new Candidate(
          ResourceType.CODE,
          ResourceHost.GITHUB,
          "https://github.com/openai/whisper",
          "openai/whisper",
          false);

  @Autowired ItemResourceRepository resources;
  @Autowired LinkCheckCacheRepository cache;
  @Autowired ItemRepository items;

  @Test
  void storesEachLinkOncePerItem() { // 005 R1.3, R2.4
    UUID item = item(NOW);

    assertThat(resources.insertIfNew(item, WHISPER, LinkOrigin.DESCRIPTION, NOW)).isTrue();
    assertThat(resources.insertIfNew(item, WHISPER, LinkOrigin.CONTENT, NOW)).isFalse();

    List<ItemResource> stored = resources.findByItem(item);
    assertThat(stored)
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.status()).isEqualTo(ResourceStatus.PENDING);
              assertThat(r.origin()).isEqualTo(LinkOrigin.DESCRIPTION);
              assertThat(r.name()).isEqualTo("openai/whisper");
            });
  }

  @Test
  void findsDueChecksForRecentItemsOnly() { // 005 R4.1
    UUID recent = item(NOW.minus(Duration.ofHours(1)));
    UUID old = item(NOW.minus(Duration.ofHours(80)));
    resources.insertIfNew(recent, WHISPER, LinkOrigin.DESCRIPTION, NOW);
    resources.insertIfNew(old, WHISPER, LinkOrigin.DESCRIPTION, NOW);

    List<ItemResource> due = resources.findDue(NOW, NOW.minus(Duration.ofHours(72)), 10);

    assertThat(due).extracting(ItemResource::itemId).containsExactly(recent);
    assertThat(resources.findDue(NOW.minusSeconds(1), NOW.minus(Duration.ofHours(72)), 10))
        .isEmpty();
  }

  @Test
  void movesThroughStatuses() { // 005 R4.2–R4.4
    UUID item = item(NOW);
    resources.insertIfNew(item, WHISPER, LinkOrigin.DESCRIPTION, NOW);
    long id = resources.findByItem(item).getFirst().id();

    resources.markFailed(id, 1, NOW.plus(Duration.ofMinutes(10)), NOW);
    assertThat(resources.findByItem(item).getFirst().status())
        .isEqualTo(ResourceStatus.CHECK_FAILED);

    resources.markVerified(id, "https://github.com/openai/whisper-v2", "openai/whisper-v2", NOW);
    assertThat(resources.findVerified(List.of(item)).get(item))
        .singleElement()
        .satisfies(r -> assertThat(r.url()).isEqualTo("https://github.com/openai/whisper-v2"));
  }

  @Test
  void deletingTheItemDeletesItsLinks() {
    UUID item = item(NOW);
    resources.insertIfNew(item, WHISPER, LinkOrigin.DESCRIPTION, NOW);

    items.deletePublishedBefore(NOW.plusSeconds(1));

    assertThat(resources.findByItem(item)).isEmpty();
  }

  @Test
  void cacheIsFreshFor24Hours() { // 005 R4.5
    cache.save(new CachedCheck("https://github.com/a/b", true, "a/b", NOW));

    assertThat(cache.findFresh("https://github.com/a/b", NOW.minus(Duration.ofHours(24))))
        .isPresent();
    assertThat(cache.findFresh("https://github.com/a/b", NOW.plusSeconds(1))).isEmpty();
  }

  private UUID item(Instant published) {
    UUID id = Ids.newId();
    items.insertIfNew(
        new Item(
            id,
            "src",
            "https://a.example/" + id,
            "T",
            "E",
            id.toString(),
            published,
            false,
            published,
            1.0,
            StoryStatus.PENDING,
            false));
    return id;
  }
}
