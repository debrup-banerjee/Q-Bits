package com.qbits.images;

import com.qbits.images.persistence.FeedImageRepository;
import com.qbits.sources.domain.Source;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Keeps the image URL a feed attached to a new item, but only for sources where a person has
 * recorded permission to show those images (spec 009 R3.2). Only the URL is stored; the image is
 * never downloaded.
 */
@Service
public class FeedImageCollector {

  private final FeedImageRepository feedImages;

  public FeedImageCollector(FeedImageRepository feedImages) {
    this.feedImages = feedImages;
  }

  /** Returns whether the URL was kept. */
  public boolean offer(UUID itemId, Source source, String imageUrl, Instant now) {
    if (imageUrl == null
        || !imageUrl.startsWith("https://")
        || source.imagePermission().isEmpty()) {
      return false;
    }
    feedImages.insertIfNew(itemId, imageUrl, now);
    return true;
  }
}
