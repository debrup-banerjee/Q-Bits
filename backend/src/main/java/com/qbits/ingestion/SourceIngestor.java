package com.qbits.ingestion;

import com.qbits.common.Ids;
import com.qbits.ingestion.domain.FetchLogEntry;
import com.qbits.ingestion.domain.FetchStatus;
import com.qbits.ingestion.domain.Item;
import com.qbits.ingestion.domain.ItemNormaliser;
import com.qbits.ingestion.domain.NormaliseResult;
import com.qbits.ingestion.domain.NormalisedEntry;
import com.qbits.ingestion.domain.RawEntry;
import com.qbits.ingestion.domain.RetryAfter;
import com.qbits.ingestion.domain.SourceState;
import com.qbits.ingestion.domain.StoryStatus;
import com.qbits.ingestion.persistence.FetchLogRepository;
import com.qbits.ingestion.persistence.ItemRepository;
import com.qbits.ingestion.persistence.SourceStateRepository;
import com.qbits.relevance.domain.RelevancePreFilter;
import com.qbits.resources.ResourceCollector;
import com.qbits.sources.domain.Source;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs the whole pipeline for one source: due check, robots.txt, conditional fetch, parse,
 * normalise, pre-filter, de-duplicate, store, log. Never throws: every failure becomes a FAILED log
 * row so other sources carry on (spec 001 R7.3).
 */
@Service
public class SourceIngestor {

  private static final Logger log = LoggerFactory.getLogger(SourceIngestor.class);
  private static final int MAX_ERROR_MESSAGE = 200;

  /** What happened for one source in one run. */
  public enum Result {
    NOT_DUE,
    DONE
  }

  private final RobotsPolicy robots;
  private final FeedFetcher fetcher;
  private final FeedParser parser;
  private final RelevancePreFilter preFilter;
  private final Deduplicator deduplicator;
  private final ItemRepository items;
  private final SourceStateRepository states;
  private final FetchLogRepository fetchLog;
  private final SourceHealthTracker health;
  private final IngestionProperties props;
  private final ResourceCollector resourceCollector;
  private final Clock clock;
  private final ItemNormaliser normaliser;

  public SourceIngestor(
      RobotsPolicy robots,
      FeedFetcher fetcher,
      FeedParser parser,
      RelevancePreFilter preFilter,
      Deduplicator deduplicator,
      ItemRepository items,
      SourceStateRepository states,
      FetchLogRepository fetchLog,
      SourceHealthTracker health,
      IngestionProperties props,
      ResourceCollector resourceCollector,
      Clock clock) {
    this.robots = robots;
    this.fetcher = fetcher;
    this.parser = parser;
    this.preFilter = preFilter;
    this.deduplicator = deduplicator;
    this.items = items;
    this.states = states;
    this.fetchLog = fetchLog;
    this.health = health;
    this.props = props;
    this.resourceCollector = resourceCollector;
    this.clock = clock;
    this.normaliser = new ItemNormaliser(props.maxAge());
  }

  public Result ingestIfDue(Source source) {
    Instant start = clock.instant();
    SourceState stored = states.find(source.id()).orElse(SourceState.initial(source.id()));
    Duration interval = interval(source);
    if (!isDue(stored, interval, start)) {
      return Result.NOT_DUE;
    }
    // Any earlier Retry-After has passed; this attempt sets a new one only if asked again.
    SourceState state = stored.withRetryAfter(null);
    try {
      RobotsDecision decision = robots.check(source.feedUrl());
      switch (decision) {
        case RobotsDecision.Disallowed d -> {
          finish(source, state, start, FetchStatus.BLOCKED_BY_ROBOTS, null, Counts.NONE, null);
          return Result.DONE;
        }
        case RobotsDecision.Unavailable u -> {
          finish(
              source, state, start, FetchStatus.ROBOTS_UNAVAILABLE, null, Counts.NONE, u.reason());
          return Result.DONE;
        }
        case RobotsDecision.Allowed a -> {
          Optional<Duration> delay = a.crawlDelay().filter(d -> d.compareTo(interval) > 0);
          if (delay.isPresent() && !isDue(state, delay.get(), start)) {
            return Result.NOT_DUE; // R3.3: Crawl-delay is longer than our interval.
          }
        }
      }
      fetchAndStore(source, state, start);
    } catch (RuntimeException e) {
      String error = shortError(e);
      log.error("source run failed source={} error={}", source.id(), error);
      finish(source, state, start, FetchStatus.FAILED, null, Counts.NONE, error);
    }
    return Result.DONE;
  }

  private void fetchAndStore(Source source, SourceState state, Instant start) {
    FetchOutcome outcome = fetcher.fetch(source.feedUrl(), state.etag(), state.lastModified());
    switch (outcome) {
      case FetchOutcome.NotModified n ->
          finish(source, state, start, FetchStatus.NOT_MODIFIED, 304, Counts.NONE, null);
      case FetchOutcome.Failed f -> {
        // A long Retry-After on 429 or 503 holds the source back until then (R7.1).
        SourceState waiting =
            state.withRetryAfter(RetryAfter.until(f.retryAfter(), start).orElse(null));
        finish(source, waiting, start, FetchStatus.FAILED, f.httpStatus(), Counts.NONE, f.error());
      }
      case FetchOutcome.Fetched f -> {
        List<RawEntry> entries;
        try {
          entries = parser.parse(f.body());
        } catch (FeedParser.FeedParseException e) {
          finish(
              source,
              state,
              start,
              FetchStatus.FAILED,
              f.httpStatus(),
              Counts.NONE,
              e.getMessage());
          return;
        }
        Counts counts = store(source, entries, start);
        finish(
            source,
            state.withValidators(f.etag(), f.lastModified()),
            start,
            FetchStatus.OK,
            f.httpStatus(),
            counts,
            null);
      }
    }
  }

  private Counts store(Source source, List<RawEntry> entries, Instant now) {
    int fresh = 0;
    int duplicate = 0;
    int notAi = 0;
    int malformed = 0;
    int tooOld = 0;
    for (RawEntry raw : entries) {
      switch (normaliser.normalise(raw, now)) {
        case NormaliseResult.Malformed m -> malformed++;
        case NormaliseResult.TooOld t -> tooOld++;
        case NormaliseResult.Accepted a -> {
          NormalisedEntry e = a.entry();
          RelevancePreFilter.Result relevance =
              preFilter.evaluate(e.title(), e.excerpt(), source.aiNative());
          if (!relevance.keep()) {
            notAi++;
          } else if (deduplicator.isDuplicate(source.id(), e, now)) {
            duplicate++;
          } else if (insert(source, e, raw, relevance.score(), now)) {
            fresh++;
          } else {
            duplicate++;
          }
        }
      }
    }
    return new Counts(fresh, duplicate, notAi, malformed, tooOld);
  }

  private boolean insert(
      Source source, NormalisedEntry e, RawEntry raw, double score, Instant now) {
    Item item = toItem(source, e, score, now);
    if (!items.insertIfNew(item)) {
      return false;
    }
    resourceCollector.collect(item.id(), raw.links(), e.title(), e.excerpt(), now); // spec 005
    return true;
  }

  private void finish(
      Source source,
      SourceState state,
      Instant start,
      FetchStatus status,
      Integer httpStatus,
      Counts counts,
      String error) {
    Instant end = clock.instant();
    fetchLog.insert(
        new FetchLogEntry(
            source.id(),
            start,
            Duration.between(start, end).toMillis(),
            status,
            httpStatus,
            counts.fresh(),
            counts.duplicate(),
            counts.notAi(),
            counts.malformed(),
            counts.tooOld(),
            error));
    states.save(health.next(state, status, start));
    log.info(
        "source fetched source={} status={} http={} new={} dup={} notAi={} bad={} tooOld={}",
        source.id(),
        status,
        httpStatus,
        counts.fresh(),
        counts.duplicate(),
        counts.notAi(),
        counts.malformed(),
        counts.tooOld());
  }

  private Duration interval(Source source) {
    Duration interval = source.intervalOverride().orElse(props.defaultInterval());
    return interval.compareTo(props.minInterval()) < 0 ? props.minInterval() : interval;
  }

  private static boolean isDue(SourceState state, Duration interval, Instant now) {
    if (state.retryAfter() != null && now.isBefore(state.retryAfter())) {
      return false; // the publisher asked us to wait (Retry-After)
    }
    return state.lastFetchedAt() == null || !now.isBefore(state.lastFetchedAt().plus(interval));
  }

  /**
   * Exception class and the first line of its message, clipped. Database errors put the failing
   * row's values on later lines ("Detail: Failing row contains …"), which may hold feed text, so
   * those lines are never logged or stored.
   */
  static String shortError(RuntimeException e) {
    String message = e.getMessage() == null ? "" : e.getMessage().lines().findFirst().orElse("");
    if (message.length() > MAX_ERROR_MESSAGE) {
      message = message.substring(0, MAX_ERROR_MESSAGE) + "…";
    }
    String name = e.getClass().getSimpleName();
    return message.isBlank() ? name : name + ": " + message;
  }

  private static Item toItem(Source source, NormalisedEntry e, double score, Instant now) {
    return new Item(
        Ids.newId(),
        source.id(),
        e.canonicalUrl(),
        e.title(),
        e.excerpt(),
        e.titleKey(),
        e.publishedAt(),
        e.dateEstimated(),
        now,
        score,
        StoryStatus.PENDING,
        false);
  }

  private record Counts(int fresh, int duplicate, int notAi, int malformed, int tooOld) {
    static final Counts NONE = new Counts(0, 0, 0, 0, 0);
  }
}
