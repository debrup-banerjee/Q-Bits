package com.qbits.stories.digest;

import static org.assertj.core.api.Assertions.assertThat;

import com.qbits.IntegrationTest;
import com.qbits.common.Ids;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class EditionRepositoryIT extends IntegrationTest {

  private static final Instant CUT = Instant.parse("2026-10-04T00:30:00Z");

  @Autowired EditionRepository editions;

  @Test
  void tracksAnEditionFromCollectingToPublished() { // 006 R1.4
    UUID id = Ids.newId();
    editions.create(
        new Edition(
            id, CUT, EditionStatus.COLLECTING, null, null, 0, 0, 0, 0, CUT, null, CUT, null));
    assertThat(editions.findOpen()).map(Edition::id).contains(id);
    assertThat(editions.findLatestPublished()).isEmpty();

    editions.markSubmitted(id, "batch_1", 12, CUT.plusSeconds(60));
    editions.markRetrying(id, "batch_2", CUT.plusSeconds(600));
    editions.markPublished(id, 9, 2, 1, CUT.plusSeconds(1800));

    Edition e = editions.find(id).orElseThrow();
    assertThat(e.status()).isEqualTo(EditionStatus.PUBLISHED);
    assertThat(e.batchId()).isEqualTo("batch_1");
    assertThat(e.retryBatchId()).isEqualTo("batch_2");
    assertThat(e.submitted()).isEqualTo(12);
    assertThat(e.notAi()).isEqualTo(2);
    assertThat(e.rejected()).isEqualTo(1);
    assertThat(e.published()).isEqualTo(9);
    assertThat(editions.findOpen()).isEmpty();
    assertThat(editions.findLatestPublished()).map(Edition::id).contains(id);
    assertThat(editions.findByCutoff(CUT)).isPresent();
  }
}
