package com.qbits.resources.domain;

import java.time.Instant;
import java.util.UUID;

/** A stored candidate link for one item, with its check state. */
public record ItemResource(
    long id,
    UUID itemId,
    ResourceType type,
    ResourceHost host,
    String url,
    String name,
    LinkOrigin origin,
    ResourceStatus status,
    int attempts,
    Instant nextCheckAt,
    Instant checkedAt) {

  /** Spaces are stored as CODE on Hugging Face with a /spaces/ URL. */
  public boolean demo() {
    return host == ResourceHost.HUGGINGFACE && url.contains("huggingface.co/spaces/");
  }

  public Candidate asCandidate() {
    return new Candidate(type, host, url, name, demo());
  }
}
