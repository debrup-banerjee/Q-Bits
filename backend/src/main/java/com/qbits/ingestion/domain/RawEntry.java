package com.qbits.ingestion.domain;

import com.qbits.common.links.FoundLink;
import java.time.Instant;
import java.util.List;

/**
 * One entry as read from a feed, before any cleanup. Any field may be null. {@code links} holds
 * only URLs found in the entry (spec 005 R1); no content text is carried. {@code imageUrl} is the
 * image the feed itself attaches to the entry (spec 009 R3.2); it is kept only for sources with a
 * recorded image permission.
 */
public record RawEntry(
    String title,
    String link,
    String descriptionHtml,
    Instant publishedAt,
    Instant updatedAt,
    List<FoundLink> links,
    String imageUrl) {

  public RawEntry {
    links = links == null ? List.of() : List.copyOf(links);
  }

  public RawEntry(
      String title,
      String link,
      String descriptionHtml,
      Instant publishedAt,
      Instant updatedAt,
      List<FoundLink> links) {
    this(title, link, descriptionHtml, publishedAt, updatedAt, links, null);
  }

  public RawEntry(
      String title, String link, String descriptionHtml, Instant publishedAt, Instant updatedAt) {
    this(title, link, descriptionHtml, publishedAt, updatedAt, List.of(), null);
  }
}
