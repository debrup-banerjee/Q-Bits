package com.qbits.ingestion;

import com.qbits.ingestion.persistence.ItemRepository;
import com.qbits.sources.SourceRegistry;
import com.qbits.sources.domain.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Keeps item visibility in line with the sources file: disabled or removed sources are hidden. */
@Component
public class SourceVisibility {

  private static final Logger log = LoggerFactory.getLogger(SourceVisibility.class);

  private final SourceRegistry registry;
  private final ItemRepository items;

  public SourceVisibility(SourceRegistry registry, ItemRepository items) {
    this.registry = registry;
    this.items = items;
  }

  public void sync() {
    int changed = items.syncVisibility(registry.enabled().stream().map(Source::id).toList());
    if (changed > 0) {
      log.info("item visibility updated rows={}", changed);
    }
  }
}
