package com.qbits.sources.domain;

import java.util.List;

/** The sources file is invalid. The message lists every problem with entry id and field. */
public class SourceConfigException extends RuntimeException {

  private final List<String> problems;

  public SourceConfigException(List<String> problems) {
    super("Invalid sources file:\n  - " + String.join("\n  - ", problems));
    this.problems = List.copyOf(problems);
  }

  public List<String> problems() {
    return problems;
  }
}
