package com.qbits.stories.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns same-event groups into the stories to hold back (spec 008 R1.4, R2.2, R2.3, R3.3). Pure and
 * deterministic: the same candidates and groups always give the same answer.
 */
public final class DuplicateRules {

  /**
   * Preference for the kept story (R2.3): an official source, then the earliest publish time, then
   * the lower source id; the story id settles anything left.
   */
  static final Comparator<GroupCandidate> KEEP_ORDER =
      Comparator.comparing((GroupCandidate c) -> !c.official())
          .thenComparing(GroupCandidate::publishedAt)
          .thenComparing(GroupCandidate::sourceId)
          .thenComparing(GroupCandidate::id);

  private DuplicateRules() {}

  /**
   * @param heldBack each held-back story id mapped to the id of the story kept in its place
   * @param groups groups that passed validation
   * @param dropped groups ignored as invalid
   */
  public record Resolution(Map<UUID, UUID> heldBack, int groups, int dropped) {
    public Resolution {
      heldBack = Map.copyOf(heldBack);
    }
  }

  /**
   * @param keepPublished true for an edition: a published story is kept when a group has one, and
   *     no published story is held back (R2.2). False for the operator clean-up (R4.1).
   */
  public static Resolution resolve(
      List<GroupCandidate> candidates, List<Set<UUID>> groups, boolean keepPublished) {
    Map<UUID, GroupCandidate> byId =
        candidates.stream()
            .collect(Collectors.toMap(GroupCandidate::id, Function.identity(), (a, b) -> a));
    Map<UUID, Integer> seen = new HashMap<>();
    groups.forEach(g -> g.forEach(id -> seen.merge(id, 1, Integer::sum)));

    // TreeMap: held-back ids in a stable order, so logs and tests read the same every run.
    Map<UUID, UUID> heldBack = new TreeMap<>();
    int accepted = 0;
    int dropped = 0;
    for (Set<UUID> group : groups) {
      boolean valid =
          group.size() >= 2
              && group.stream().allMatch(byId::containsKey)
              && group.stream().allMatch(id -> seen.get(id) == 1);
      if (!valid) {
        dropped++;
        continue;
      }
      accepted++;
      List<GroupCandidate> members = new ArrayList<>(group.stream().map(byId::get).toList());
      boolean anyPublished = members.stream().anyMatch(GroupCandidate::published);
      List<GroupCandidate> pool =
          keepPublished && anyPublished
              ? members.stream().filter(GroupCandidate::published).toList()
              : members;
      GroupCandidate kept = pool.stream().min(KEEP_ORDER).orElseThrow();
      for (GroupCandidate m : members) {
        boolean withdrawsLive = keepPublished && m.published();
        if (!m.id().equals(kept.id()) && !withdrawsLive) {
          heldBack.put(m.id(), kept.id());
        }
      }
    }
    return new Resolution(heldBack, accepted, dropped);
  }
}
