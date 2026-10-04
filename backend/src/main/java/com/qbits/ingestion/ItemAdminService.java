package com.qbits.ingestion;

import com.qbits.ingestion.persistence.ItemRepository;
import org.springframework.stereotype.Service;

/** Operator actions on stored items, for other features to call (spec 001 R8.2). */
@Service
public class ItemAdminService {

  private final ItemRepository items;

  public ItemAdminService(ItemRepository items) {
    this.items = items;
  }

  /** Deletes every stored item of the source. Returns how many were removed. */
  public int purgeSource(String sourceId) {
    return items.deleteBySource(sourceId);
  }
}
