package com.qbits.resources;

import com.qbits.common.links.FoundLink;
import com.qbits.resources.domain.Candidate;
import com.qbits.resources.domain.ResourceLinkNormaliser;
import com.qbits.resources.domain.ResourceRelevance;
import com.qbits.resources.persistence.ItemResourceRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Turns the URLs of a newly ingested item into PENDING open-source candidates (spec 005 R1–R3). */
@Service
public class ResourceCollector {

  /** Upper bound on candidates per item, to keep host checks small. */
  static final int MAX_CANDIDATES = 10;

  private final ItemResourceRepository resources;
  private final ResourceLinkNormaliser normaliser = new ResourceLinkNormaliser();
  private final ResourceRelevance relevance = new ResourceRelevance();

  public ResourceCollector(ItemResourceRepository resources) {
    this.resources = resources;
  }

  /** Returns how many candidates were stored. */
  public int collect(
      UUID itemId, List<FoundLink> links, String title, String excerpt, Instant now) {
    Set<String> seen = new HashSet<>();
    int stored = 0;
    for (FoundLink link : links) {
      if (stored >= MAX_CANDIDATES) {
        break;
      }
      Optional<Candidate> candidate = normaliser.normalise(link.url());
      if (candidate.isEmpty()
          || !seen.add(candidate.get().url())
          || !relevance.keep(candidate.get(), link.origin(), title, excerpt)) {
        continue;
      }
      if (resources.insertIfNew(itemId, candidate.get(), link.origin(), now)) {
        stored++;
      }
    }
    return stored;
  }
}
