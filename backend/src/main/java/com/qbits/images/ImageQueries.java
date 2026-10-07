package com.qbits.images;

import com.qbits.images.domain.StoryImage;
import com.qbits.images.persistence.StoryImageRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Pictures for stories as clients see them (spec 009 R5). Stories without one are left out. */
@Service
public class ImageQueries {

  private final StoryImageRepository images;

  public ImageQueries(StoryImageRepository images) {
    this.images = images;
  }

  public Map<UUID, StoryImage> forStories(Collection<UUID> itemIds) {
    Map<UUID, StoryImage> out = new HashMap<>();
    images
        .findFound(itemIds)
        .forEach(
            (id, f) -> {
              boolean photo = "PHOTO".equals(f.kind());
              out.put(
                  id,
                  new StoryImage(
                      f.kind().toLowerCase(Locale.ROOT),
                      f.url(),
                      f.alt(),
                      f.credit(),
                      f.creditUrl(),
                      f.provider(),
                      photo ? PexelsClient.HOME : f.pageUrl(),
                      f.color()));
            });
    return out;
  }
}
