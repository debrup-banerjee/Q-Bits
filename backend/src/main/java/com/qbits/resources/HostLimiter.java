package com.qbits.resources;

import com.qbits.resources.ResourcesProperties.RateLimit;
import com.qbits.resources.domain.ResourceHost;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;

/**
 * Per-host call budget: at most N calls in any hour and a minimum gap between calls (spec 005
 * R4.6). Only one instance runs the check job at a time (database lock), so in-memory state is
 * enough.
 */
public class HostLimiter {

  private final Map<ResourceHost, RateLimit> limits;
  private final Map<ResourceHost, Deque<Instant>> calls = new EnumMap<>(ResourceHost.class);
  private final Clock clock;

  public HostLimiter(Map<ResourceHost, RateLimit> limits, Clock clock) {
    this.limits = limits;
    this.clock = clock;
  }

  /** Takes one call from the host's budget if allowed right now. */
  public synchronized boolean tryAcquire(ResourceHost host) {
    RateLimit limit = limits.get(host);
    if (limit == null) {
      return false;
    }
    Instant now = clock.instant();
    Deque<Instant> recent = calls.computeIfAbsent(host, h -> new ArrayDeque<>());
    while (!recent.isEmpty() && recent.peekFirst().isBefore(now.minus(Duration.ofHours(1)))) {
      recent.pollFirst();
    }
    if (recent.size() >= limit.perHour()) {
      return false;
    }
    if (!recent.isEmpty() && recent.peekLast().plus(limit.minSpacing()).isAfter(now)) {
      return false;
    }
    recent.addLast(now);
    return true;
  }
}
