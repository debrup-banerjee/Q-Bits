package com.qbits.ingestion;

import com.qbits.ingestion.persistence.ItemRepository;
import com.qbits.sources.SourceRegistry;
import com.qbits.sources.domain.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Keeps item visibility in line with the sources file: disabled or removed sources are hidden (spec
 * 001 R8.1). Runs as soon as the app has started, and again at the start of every ingestion run.
 */
@Component
public class SourceVisibility {

  private static final Logger log = LoggerFactory.getLogger(SourceVisibility.class);

  private final SourceRegistry registry;
  private final ItemRepository items;

  public SourceVisibility(SourceRegistry registry, ItemRepository items) {
    this.registry = registry;
    this.items = items;
  }

  /** Hides a newly disabled source's items right after a restart, not at the first run. */
  @EventListener(ApplicationReadyEvent.class)
  public void syncAtStartup() {
    sync();
  }

  public void sync() {
    int changed = items.syncVisibility(registry.enabled().stream().map(Source::id).toList());
    if (changed > 0) {
      log.info("item visibility updated rows={}", changed);
    }
  }
}
