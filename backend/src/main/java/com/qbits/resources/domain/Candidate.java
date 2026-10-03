package com.qbits.resources.domain;

/**
 * A normalised open-source link (spec 005 R2). {@code name} is the short path shown to readers,
 * such as {@code openai/whisper} or {@code 2410.01234}. {@code demo} marks Hugging Face Spaces.
 */
public record Candidate(
    ResourceType type, ResourceHost host, String url, String name, boolean demo) {

  /** Fixed label from type and host, never generated text (spec 005 R5.2). */
  public String label() {
    if (demo) {
      return "Demo on " + host.displayName();
    }
    return switch (type) {
          case CODE -> "Code";
          case MODEL -> "Model";
          case DATASET -> "Dataset";
          case PAPER -> "Paper";
        }
        + " on "
        + host.displayName();
  }
}
