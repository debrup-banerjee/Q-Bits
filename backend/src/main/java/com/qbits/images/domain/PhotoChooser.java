package com.qbits.images.domain;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Picks the first usable search result (spec 009 R2.4): an https hotlink with a named photographer,
 * not already used by another story recently, so the feed does not repeat itself.
 */
public final class PhotoChooser {

  private PhotoChooser() {}

  public static Optional<PhotoCandidate> choose(
      List<PhotoCandidate> results, Set<String> recentlyUsedIds) {
    return results.stream()
        .filter(p -> p.id() != null && !p.id().isBlank())
        .filter(p -> p.imageUrl() != null && p.imageUrl().startsWith("https://"))
        .filter(p -> p.photographer() != null && !p.photographer().isBlank())
        .filter(p -> !recentlyUsedIds.contains(p.id()))
        .findFirst();
  }
}
