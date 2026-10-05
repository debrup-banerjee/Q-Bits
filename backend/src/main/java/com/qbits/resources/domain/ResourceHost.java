package com.qbits.resources.domain;

/** The allowlisted hosts (principles: open-source links). */
public enum ResourceHost {
  GITHUB("GitHub"),
  GITLAB("GitLab"),
  HUGGINGFACE("Hugging Face"),
  ARXIV("arXiv");

  private final String displayName;

  ResourceHost(String displayName) {
    this.displayName = displayName;
  }

  public String displayName() {
    return displayName;
  }
}
