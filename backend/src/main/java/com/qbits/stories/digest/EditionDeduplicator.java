package com.qbits.stories.digest;

import com.qbits.ingestion.ItemStore;
import com.qbits.sources.SourceRegistry;
import com.qbits.sources.domain.Source;
import com.qbits.stories.DedupeProperties;
import com.qbits.stories.StoryGrouper;
import com.qbits.stories.StoryWriter;
import com.qbits.stories.StoryWriterProperties;
import com.qbits.stories.domain.DuplicateRules;
import com.qbits.stories.domain.DuplicateRules.Resolution;
import com.qbits.stories.domain.GroupCandidate;
import com.qbits.stories.persistence.StoryRepository;
import com.qbits.stories.persistence.StoryRepository.CandidateRow;
import com.qbits.stories.persistence.WriterBudgetRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * One story per event (spec 008). Before an edition is published, groups its written stories with
 * the stories already live and holds back the repeats; also runs the operator clean-up.
 */
@Component
public class EditionDeduplicator {

  private static final Logger log = LoggerFactory.getLogger(EditionDeduplicator.class);

  private final EditionPublisher publisher;
  private final EditionRepository editions;
  private final StoryRepository stories;
  private final ItemStore items;
  private final SourceRegistry registry;
  private final StoryGrouper grouper;
  private final WriterBudgetRepository budget;
  private final StoryWriterProperties writerProps;
  private final DedupeProperties props;
  private final DigestProperties digest;
  private final TransactionTemplate tx;

  public EditionDeduplicator(
      EditionPublisher publisher,
      EditionRepository editions,
      StoryRepository stories,
      ItemStore items,
      SourceRegistry registry,
      StoryGrouper grouper,
      WriterBudgetRepository budget,
      StoryWriterProperties writerProps,
      DedupeProperties props,
      DigestProperties digest,
      TransactionTemplate tx) {
    this.publisher = publisher;
    this.editions = editions;
    this.stories = stories;
    this.items = items;
    this.registry = registry;
    this.grouper = grouper;
    this.budget = budget;
    this.writerProps = writerProps;
    this.props = props;
    this.digest = digest;
    this.tx = tx;
  }

  /**
   * Publishes the edition with one story per event. Returns false when grouping failed and there is
   * still time before the deadline: nothing changed, and the next tick tries again (R3.1).
   */
  public boolean publish(Edition e, Instant now) {
    if (!props.enabled()) {
      publisher.publish(e.id(), now);
      return true;
    }
    List<GroupCandidate> candidates =
        candidates(
            stories.editionCandidates(e.id(), e.cutoffAt().minus(EditionSchedule.MAX_WINDOW)));
    if (candidates.stream().allMatch(GroupCandidate::published)) {
      publisher.publish(e.id(), now); // nothing new to check
      return true;
    }
    if (!budget.tryTake(LocalDate.ofInstant(now, ZoneOffset.UTC), writerProps.dailyCallCap())) {
      log.warn("dedupe skipped reason=cap edition={}", e.id()); // R3.2
      publisher.publish(e.id(), now);
      return true;
    }
    List<Set<UUID>> groups;
    try {
      groups = grouper.group(candidates);
    } catch (StoryWriter.WriterUnavailable | StoryWriter.WriterRejected ex) {
      if (now.plus(digest.tick()).isBefore(e.cutoffAt().plus(digest.publishDeadline()))) {
        editions.incrementDedupeAttempts(e.id());
        log.warn("dedupe failed; will retry edition={} reason={}", e.id(), ex.getMessage());
        return false;
      }
      log.warn("dedupe skipped reason=failed edition={} error={}", e.id(), ex.getMessage());
      publisher.publish(e.id(), now);
      return true;
    }
    Resolution r = DuplicateRules.resolve(candidates, groups, true);
    log.info(
        "edition deduped edition={} candidates={} groups={} duplicates={} dropped={}",
        e.id(),
        candidates.size(),
        r.groups(),
        r.heldBack().size(),
        r.dropped());
    publisher.publish(e.id(), now, r.heldBack());
    return true;
  }

  /** What the clean-up did: the candidates it looked at and each held-back id's kept id. */
  public record CleanUp(List<GroupCandidate> candidates, Map<UUID, UUID> heldBack, int dropped) {}

  /**
   * Groups the live stories in the window ending at {@code windowEnd} and holds back every repeat,
   * live ones included, keeping one per event by the usual preference (R4.1). Counts as one call.
   *
   * @throws IllegalStateException when today's calls are used up
   */
  public CleanUp cleanUpPublished(Instant windowEnd, Instant now) {
    List<GroupCandidate> candidates =
        candidates(stories.publishedCandidates(windowEnd.minus(EditionSchedule.MAX_WINDOW)));
    if (!budget.tryTake(LocalDate.ofInstant(now, ZoneOffset.UTC), writerProps.dailyCallCap())) {
      throw new IllegalStateException("today's summary-service calls are used up; try tomorrow");
    }
    Resolution r = DuplicateRules.resolve(candidates, grouper.group(candidates), false);
    tx.executeWithoutResult(status -> r.heldBack().forEach(items::markDuplicate));
    log.warn(
        "live duplicates held back candidates={} groups={} duplicates={}",
        candidates.size(),
        r.groups(),
        r.heldBack().size());
    return new CleanUp(candidates, r.heldBack(), r.dropped());
  }

  private List<GroupCandidate> candidates(List<CandidateRow> rows) {
    return rows.stream()
        .map(
            row -> {
              Optional<Source> source = registry.find(row.sourceId());
              return new GroupCandidate(
                  row.id(),
                  row.headline(),
                  row.summary(),
                  row.section(),
                  row.sourceId(),
                  source.map(Source::name).orElse(row.sourceId()),
                  source.map(Source::official).orElse(false),
                  row.publishedAt(),
                  row.published());
            })
        .toList();
  }
}
