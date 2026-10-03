package com.qbits.ingestion;

import java.time.Duration;
import java.util.Optional;

/** Whether a feed URL may be fetched according to its host's robots.txt (spec 001 R3). */
public sealed interface RobotsDecision {

  /** Fetching is allowed. Crawl-delay, if the host set one. */
  record Allowed(Optional<Duration> crawlDelay) implements RobotsDecision {}

  /** robots.txt disallows the feed path (R3.2). */
  record Disallowed() implements RobotsDecision {}

  /** robots.txt could not be read (5xx or timeout); skip this cycle (R3.4). */
  record Unavailable(String reason) implements RobotsDecision {}
}
