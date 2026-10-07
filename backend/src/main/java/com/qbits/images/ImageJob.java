package com.qbits.images;

import com.qbits.images.domain.ImageQueryFallback;
import com.qbits.images.domain.PhotoCandidate;
import com.qbits.images.domain.PhotoChooser;
import com.qbits.images.domain.QueryRules;
import com.qbits.images.persistence.StoryImageRepository;
import com.qbits.images.persistence.StoryImageRepository.Due;
import com.qbits.sources.SourceRegistry;
import com.qbits.sources.domain.ImagePermission;
import com.qbits.sources.domain.Source;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Finds a picture for each new story (spec 009 R1–R4), in this order: the source's own feed image
 * when a person has recorded permission for that source; else a free Pexels photo found from a
 * neutral search phrase. When neither is available the story is marked NONE and clients draw its
 * cover art. Runs on one instance at a time.
 */
@Service
public class ImageJob {

  private static final Logger log = LoggerFactory.getLogger(ImageJob.class);
  static final Duration WINDOW = Duration.ofHours(72);
  static final String PEXELS = "Pexels";

  private final StoryImageRepository images;
  private final SourceRegistry sources;
  private final PexelsClient pexels;
  private final ObjectProvider<ImageQueryWriter> writer;
  private final ImagesProperties props;
  private final LockingTaskExecutor locks;
  private final Clock clock;

  private LocalDate callDay;
  private int callsToday;

  public ImageJob(
      StoryImageRepository images,
      SourceRegistry sources,
      PexelsClient pexels,
      ObjectProvider<ImageQueryWriter> writer,
      ImagesProperties props,
      LockingTaskExecutor locks,
      Clock clock) {
    this.images = images;
    this.sources = sources;
    this.pexels = pexels;
    this.writer = writer;
    this.props = props;
    this.locks = locks;
    this.clock = clock;
  }

  public void runOnce() {
    LockConfiguration lock =
        new LockConfiguration(Instant.now(), "images", Duration.ofMinutes(30), Duration.ZERO);
    locks.executeWithLock((Runnable) this::run, lock);
  }

  private enum Outcome {
    PUBLISHER,
    PHOTO,
    NONE,
    RETRY,
    WAITING,
    THROTTLED
  }

  private void run() {
    Instant now = clock.instant();
    List<Due> due = images.findDue(now.minus(WINDOW), now, props.batchSize());
    int publisher = 0;
    int photo = 0;
    int none = 0;
    int retry = 0;
    for (Due d : due) {
      Outcome outcome;
      try {
        outcome = pick(d, now);
      } catch (RuntimeException e) {
        // One bad row must never end the run; put it on the retry schedule and move on.
        log.warn("image pick failed item={} error={}", d.itemId(), e.getClass().getSimpleName());
        retryOrGiveUp(d, null, now);
        outcome = Outcome.RETRY;
      }
      switch (outcome) {
        case PUBLISHER -> publisher++;
        case PHOTO -> photo++;
        case NONE -> none++;
        case RETRY -> retry++;
        case WAITING -> {}
        case THROTTLED -> {}
      }
      if (outcome == Outcome.THROTTLED) {
        break; // the hourly budget is spent; the rest wait for the next tick
      }
    }
    if (publisher + photo + none + retry > 0) {
      log.info(
          "story images due={} publisher={} photo={} none={} retry={}",
          due.size(),
          publisher,
          photo,
          none,
          retry);
    }
  }

  private Outcome pick(Due d, Instant now) {
    Optional<Source> source = sources.find(d.sourceId());
    Optional<ImagePermission> permission = source.flatMap(Source::imagePermission);
    if (d.feedImageUrl() != null && permission.isPresent()) {
      images.saveFound(
          d.itemId(),
          "PUBLISHER",
          source.get().name(),
          null,
          d.feedImageUrl(),
          source.get().homepage().toString(),
          "Image supplied by " + source.get().name(),
          permission.get().credit(),
          null,
          null,
          null,
          now);
      return Outcome.PUBLISHER;
    }
    if (!pexels.configured()) {
      return Outcome.WAITING; // no photo library yet: cover art until a key is set
    }
    if (!pexels.tryAcquire()) {
      return Outcome.THROTTLED;
    }
    String fallback = ImageQueryFallback.queryFor(d.section(), d.keyTerms());
    String query = modelPhrase(d, now).orElse(fallback);
    List<PhotoCandidate> results;
    try {
      results = pexels.search(query);
    } catch (PexelsClient.SearchFailed e) {
      log.warn("photo search failed item={} reason={}", d.itemId(), e.getMessage());
      retryOrGiveUp(d, query, now);
      return Outcome.RETRY;
    }
    Set<String> recent = images.recentProviderIds(PEXELS, now.minus(props.reuseWindow()));
    Optional<PhotoCandidate> chosen = PhotoChooser.choose(results, recent);
    if (chosen.isEmpty() && !query.equals(fallback) && pexels.tryAcquire()) {
      query = fallback;
      chosen = PhotoChooser.choose(pexels.search(query), recent);
    }
    if (chosen.isEmpty()) {
      images.saveNone(d.itemId(), query, now);
      return Outcome.NONE;
    }
    PhotoCandidate p = chosen.get();
    images.saveFound(
        d.itemId(),
        "PHOTO",
        PEXELS,
        p.id(),
        p.imageUrl(),
        p.pageUrl(),
        alt(p.description(), query),
        p.photographer(),
        p.photographerUrl(),
        p.color() != null && p.color().matches("#[0-9a-fA-F]{6}") ? p.color() : null,
        query,
        now);
    return Outcome.PHOTO;
  }

  /** The model's phrase if it passes the rules and the daily cap allows the call. */
  private Optional<String> modelPhrase(Due d, Instant now) {
    ImageQueryWriter w = writer.getIfAvailable();
    if (w == null || !takeDailyCall(now)) {
      return Optional.empty();
    }
    return w.phraseFor(d.headline(), d.summary(), d.keyTerms())
        .flatMap(p -> QueryRules.clean(p, QueryRules.namesIn(d.headline(), d.keyTerms())));
  }

  private boolean takeDailyCall(Instant now) {
    LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
    if (!today.equals(callDay)) {
      callDay = today;
      callsToday = 0;
    }
    if (callsToday >= props.query().dailyCallCap()) {
      return false;
    }
    callsToday++;
    return true;
  }

  /** Retries on the configured delays, then gives up as NONE so the story keeps its cover art. */
  private void retryOrGiveUp(Due d, String query, Instant now) {
    int attempts = d.attempts() + 1;
    List<Duration> delays = props.retryDelays();
    if (attempts > delays.size()) {
      images.saveNone(d.itemId(), query, now);
      return;
    }
    images.saveRetry(d.itemId(), attempts, now.plus(delays.get(attempts - 1)), query, now);
  }

  private static String alt(String description, String query) {
    String text = description == null || description.isBlank() ? query : description.strip();
    return text.length() > 300 ? text.substring(0, 300) : text;
  }
}
