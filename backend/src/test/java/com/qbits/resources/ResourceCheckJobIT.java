package com.qbits.resources;

import static org.assertj.core.api.Assertions.assertThat;

import com.qbits.IntegrationTest;
import com.qbits.MutableClock;
import com.qbits.common.Ids;
import com.qbits.ingestion.domain.Item;
import com.qbits.ingestion.domain.StoryStatus;
import com.qbits.ingestion.persistence.ItemRepository;
import com.qbits.resources.ResourcesProperties.RateLimit;
import com.qbits.resources.domain.Candidate;
import com.qbits.resources.domain.ItemResource;
import com.qbits.resources.domain.LinkOrigin;
import com.qbits.resources.domain.ResourceHost;
import com.qbits.resources.domain.ResourceLinkNormaliser;
import com.qbits.resources.domain.ResourceStatus;
import com.qbits.resources.persistence.ItemResourceRepository;
import com.qbits.resources.persistence.LinkCheckCacheRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ResourceCheckJobIT extends IntegrationTest {

  private static final Instant NOW = Instant.parse("2026-10-03T06:00:00Z");

  @Autowired ItemResourceRepository resources;
  @Autowired LinkCheckCacheRepository cache;
  @Autowired ItemRepository items;
  @Autowired LockingTaskExecutor locks;

  private final ResourceLinkNormaliser normaliser = new ResourceLinkNormaliser();
  private MutableClock clock;
  private ScriptedChecker checker;

  /** Answers from a script; records every call. */
  static class ScriptedChecker implements LinkChecker {
    final Deque<CheckResult> script = new ArrayDeque<>();
    final List<String> calls = new ArrayList<>();
    CheckResult fallback = new CheckResult.Found(null);

    @Override
    public CheckResult check(Candidate c) {
      calls.add(c.url());
      return script.isEmpty() ? fallback : script.poll();
    }
  }

  @BeforeEach
  void setUp() {
    clock = new MutableClock(NOW);
    checker = new ScriptedChecker();
  }

  private ResourceCheckJob job() {
    RateLimit any = new RateLimit(100, Duration.ZERO);
    ResourcesProperties props =
        new ResourcesProperties(
            Duration.ofMinutes(5),
            30,
            3,
            Duration.ofHours(24),
            List.of(Duration.ofMinutes(10), Duration.ofHours(1), Duration.ofHours(6)),
            null,
            Map.of(ResourceHost.GITHUB, "x"),
            Map.of(ResourceHost.GITHUB, any));
    return new ResourceCheckJob(resources, cache, checker, props, locks, clock);
  }

  @Test
  void verifiesAndSharesTheResultAcrossItems() { // 005 R4.2, R4.5
    UUID a = item(NOW);
    UUID b = item(NOW);
    add(a, "https://github.com/openai/whisper");
    add(b, "https://github.com/openai/whisper");

    job().runOnce();

    assertThat(checker.calls).hasSize(1);
    assertThat(status(a)).isEqualTo(ResourceStatus.VERIFIED);
    assertThat(status(b)).isEqualTo(ResourceStatus.VERIFIED);
  }

  @Test
  void usesTheRenamedGitHubRepo() { // 005 R4.2
    UUID a = item(NOW);
    add(a, "https://github.com/old/name");
    checker.script.add(new CheckResult.Found("new-org/new-name"));

    job().runOnce();

    ItemResource r = resources.findByItem(a).getFirst();
    assertThat(r.url()).isEqualTo("https://github.com/new-org/new-name");
    assertThat(r.name()).isEqualTo("new-org/new-name");
  }

  @Test
  void missingLinksAreNeverCheckedAgain() { // 005 R4.3
    UUID a = item(NOW);
    add(a, "https://github.com/no/such");
    checker.script.add(new CheckResult.Missing());
    ResourceCheckJob job = job();

    job.runOnce();
    clock.advance(Duration.ofDays(1));
    job.runOnce();

    assertThat(status(a)).isEqualTo(ResourceStatus.NOT_FOUND);
    assertThat(checker.calls).hasSize(1);
  }

  @Test
  void retriesAfter10Minutes1Hour6HoursThenGivesUp() { // 005 R4.4
    UUID a = item(NOW);
    add(a, "https://github.com/flaky/repo");
    checker.fallback = new CheckResult.Failed("HTTP 503");
    ResourceCheckJob job = job();

    job.runOnce();
    assertThat(status(a)).isEqualTo(ResourceStatus.CHECK_FAILED);
    clock.advance(Duration.ofMinutes(9));
    job.runOnce();
    assertThat(checker.calls).hasSize(1);

    clock.advance(Duration.ofMinutes(1));
    job.runOnce();
    clock.advance(Duration.ofHours(1));
    job.runOnce();
    clock.advance(Duration.ofHours(6));
    job.runOnce();

    assertThat(checker.calls).hasSize(4);
    assertThat(status(a)).isEqualTo(ResourceStatus.NOT_FOUND);
  }

  @Test
  void throttledHostStopsForThisRunOnly() { // 005 R4.6
    UUID a = item(NOW);
    add(a, "https://github.com/a/one");
    add(a, "https://github.com/a/two");
    add(a, "https://huggingface.co/org/model");
    checker.script.add(new CheckResult.Throttled());

    job().runOnce();

    assertThat(checker.calls)
        .containsExactly("https://github.com/a/one", "https://huggingface.co/org/model");
    assertThat(resources.findByItem(a))
        .extracting(ItemResource::status)
        .containsExactly(ResourceStatus.PENDING, ResourceStatus.PENDING, ResourceStatus.VERIFIED);
  }

  @Test
  void skipsItemsOlderThan72Hours() {
    UUID old = item(NOW.minus(Duration.ofHours(73)));
    add(old, "https://github.com/a/b");

    job().runOnce();

    assertThat(checker.calls).isEmpty();
  }

  private void add(UUID item, String url) {
    resources.insertIfNew(
        item, normaliser.normalise(url).orElseThrow(), LinkOrigin.DESCRIPTION, NOW);
  }

  private ResourceStatus status(UUID item) {
    return resources.findByItem(item).getFirst().status();
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
