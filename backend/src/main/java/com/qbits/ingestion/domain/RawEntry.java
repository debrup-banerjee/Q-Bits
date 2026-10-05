package com.qbits.ingestion.domain;

import com.qbits.common.links.FoundLink;
import java.time.Instant;
import java.util.List;

/**
 * One entry as read from a feed, before any cleanup. Any field may be null. {@code links} holds
 * only URLs found in the entry (spec 005 R1); no content text is carried.
 */
public record RawEntry(
    String title,
    String link,
    String descriptionHtml,
    Instant publishedAt,
    Instant updatedAt,
    List<FoundLink> links) {

  public RawEntry {
    links = links == null ? List.of() : List.copyOf(links);
  }

  public RawEntry(
      String title, String link, String descriptionHtml, Instant publishedAt, Instant updatedAt) {
    this(title, link, descriptionHtml, publishedAt, updatedAt, List.of());
  }
}
