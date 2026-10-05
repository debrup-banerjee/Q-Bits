package com.qbits.resources.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.qbits.IntegrationTest;
import com.qbits.resources.persistence.LinkCheckCacheRepository.CachedCheck;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class LinkCheckCacheRepositoryIT extends IntegrationTest {

  private static final Instant NOW = Instant.parse("2026-10-03T06:00:00Z");
  private static final String URL = "https://github.com/cache-test/repo";

  @Autowired LinkCheckCacheRepository cache;

  @Test
  void isFreshFor24Hours() { // 005 R4.5
    cache.save(new CachedCheck(URL, true, "cache-test/repo", NOW));

    assertThat(cache.findFresh(URL, NOW.minus(Duration.ofHours(24))))
        .hasValueSatisfying(
            c -> {
              assertThat(c.found()).isTrue();
              assertThat(c.canonical()).isEqualTo("cache-test/repo");
            });
    assertThat(cache.findFresh(URL, NOW.plusSeconds(1))).isEmpty();
  }

  @Test
  void aNewerCheckReplacesTheOldOne() { // 005 R4.5, R4.7
    cache.save(new CachedCheck(URL, true, "cache-test/repo", NOW));
    cache.save(new CachedCheck(URL, false, null, NOW.plus(Duration.ofHours(25))));

    assertThat(cache.findFresh(URL, NOW.plus(Duration.ofHours(24))))
        .hasValueSatisfying(
            c -> {
              assertThat(c.found()).isFalse();
              assertThat(c.canonical()).isNull();
            });
  }

  @Test
  void unknownUrlIsAMiss() { // 005 R4.5
    assertThat(
            cache.findFresh("https://github.com/cache-test/never", NOW.minus(Duration.ofDays(1))))
        .isEmpty();
  }
}
