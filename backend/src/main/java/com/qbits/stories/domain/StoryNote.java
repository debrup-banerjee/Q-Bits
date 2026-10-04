package com.qbits.stories.domain;

/**
 * The note kept on an item that is not shown: the not-AI reason (spec 002 R2.2) or why it was
 * rejected (R8.3). Fits the 300-character column, so recording the outcome never fails.
 */
public final class StoryNote {

  public static final int MAX_CHARS = 300;

  private StoryNote() {}

  /**
   * One line of plain text of at most {@link #MAX_CHARS} characters: control characters and runs of
   * white space become one space, and a longer note is cut at a character boundary.
   */
  public static String of(String text) {
    if (text == null) {
      return null;
    }
    String line = text.replaceAll("[\\p{Cntrl}\\s]+", " ").strip();
    if (line.codePointCount(0, line.length()) <= MAX_CHARS) {
      return line;
    }
    return line.substring(0, line.offsetByCodePoints(0, MAX_CHARS)).strip();
  }
}
