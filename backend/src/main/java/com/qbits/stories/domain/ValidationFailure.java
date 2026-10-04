package com.qbits.stories.domain;

/** One broken rule, with a message the summary service can act on when retrying. */
public record ValidationFailure(String rule, String message) {

  @Override
  public String toString() {
    return rule + ": " + message;
  }
}
