package com.qbits.resources;

import com.qbits.resources.domain.Candidate;
import com.qbits.resources.domain.ItemResource;
import com.qbits.resources.domain.ResourceHost;
import com.qbits.resources.persistence.ItemResourceRepository;
import com.qbits.resources.persistence.LinkCheckCacheRepository;
import com.qbits.resources.persistence.LinkCheckCacheRepository.CachedCheck;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Checks pending open-source links through the hosts' APIs, with a shared 24-hour cache and a retry
 * schedule (spec 005 R4). Runs on one instance at a time.
 */
@Service
public class ResourceCheckJob {

  private static final Logger log = LoggerFactory.getLogger(ResourceCheckJob.class);
  static final Duration WINDOW = Duration.ofHours(72);

  private final ItemResourceRepository resources;
  private final LinkCheckCacheRepository cache;
  private final LinkChecker hosts;
  private final ResourcesProperties props;
  private final LockingTaskExecutor locks;
  private final Clock clock;

  public ResourceCheckJob(
      ItemResourceRepository resources,
      LinkCheckCacheRepository cache,
      LinkChecker hosts,
      ResourcesProperties props,
      LockingTaskExecutor locks,
      Clock clock) {
    this.resources = resources;
    this.cache = cache;
    this.hosts = hosts;
    this.props = props;
    this.locks = locks;
    this.clock = clock;
  }

  public void runOnce() {
    LockConfiguration lock =
        new LockConfiguration(Instant.now(), "resources", Duration.ofMinutes(30), Duration.ZERO);
    locks.executeWithLock((Runnable) this::run, lock);
  }

  private void run() {
    Instant now = clock.instant();
    List<ItemResource> due = resources.findDue(now, now.minus(WINDOW), props.batchSize());
    Set<ResourceHost> throttled = EnumSet.noneOf(ResourceHost.class);
    int verified = 0;
    int missing = 0;
    int failed = 0;
    for (ItemResource r : due) {
      Optional<CachedCheck> cached = cache.findFresh(r.url(), now.minus(props.cacheTtl()));
      if (cached.isPresent()) {
        apply(r, cached.get().found(), cached.get().canonical(), now);
        continue;
      }
      if (throttled.contains(r.host())) {
        continue;
      }
      CheckResult result = hosts.check(r.asCandidate());
      switch (result) {
        case CheckResult.Found f -> {
          cache.save(new CachedCheck(r.url(), true, f.canonicalName(), now));
          apply(r, true, f.canonicalName(), now);
          verified++;
        }
        case CheckResult.Missing m -> {
          cache.save(new CachedCheck(r.url(), false, null, now));
          apply(r, false, null, now);
          missing++;
        }
        case CheckResult.Failed f -> {
          retryOrGiveUp(r, now);
          failed++;
        }
        case CheckResult.Throttled t -> throttled.add(r.host());
      }
    }
    if (!due.isEmpty()) {
      log.info(
          "link checks due={} verified={} missing={} failed={} throttledHosts={}",
          due.size(),
          verified,
          missing,
          failed,
          throttled);
    }
  }

  private void apply(ItemResource r, boolean found, String canonicalName, Instant now) {
    if (!found) {
      resources.markNotFound(r.id(), now);
      return;
    }
    if (r.host() == ResourceHost.GITHUB && canonicalName != null && !canonicalName.isBlank()) {
      // GitHub reports the current name after a rename (R4.2).
      Candidate renamed =
          new Candidate(
              r.type(), r.host(), "https://github.com/" + canonicalName, canonicalName, false);
      resources.markVerified(r.id(), renamed.url(), renamed.name(), now);
    } else {
      resources.markVerified(r.id(), r.url(), r.name(), now);
    }
  }

  /** Retries after 10 min, 1 h, 6 h; then gives up as NOT_FOUND (R4.4). */
  private void retryOrGiveUp(ItemResource r, Instant now) {
    int attempts = r.attempts() + 1;
    List<Duration> delays = props.retryDelays();
    if (attempts > delays.size()) {
      resources.markNotFound(r.id(), now);
      return;
    }
    resources.markFailed(r.id(), attempts, now.plus(delays.get(attempts - 1)), now);
  }
}
