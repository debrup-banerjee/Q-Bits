package com.qbits.stories;

import com.qbits.stories.domain.GroupCandidate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Finds stories that report the same event (spec 008 R1). With {@link StoryWriter}, the only way
 * the app reaches the summary service (conventions). Implementations send only the candidates' own
 * headline, summary, section, source name and publish time (R1.3).
 */
public interface StoryGrouper {

  /**
   * Groups of two or more candidate ids that report the same event. Stories in no group are unique.
   * The answer is checked by {@code DuplicateRules}, so it may contain mistakes.
   *
   * @throws StoryWriter.WriterUnavailable when the service is down, slow or rate-limited
   * @throws StoryWriter.WriterRejected when the service refused this request
   */
  List<Set<UUID>> group(List<GroupCandidate> candidates);
}
