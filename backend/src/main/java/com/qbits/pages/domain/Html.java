package com.qbits.pages.domain;

/** Escaping for text placed into HTML and XML, and for JSON placed inside a script element. */
public final class Html {

  private Html() {}

  /** Escapes text for element content and quoted attribute values, in HTML and XML (007 R2.7). */
  public static String text(String value) {
    StringBuilder out = new StringBuilder(value.length() + 16);
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '&' -> out.append("&amp;");
        case '<' -> out.append("&lt;");
        case '>' -> out.append("&gt;");
        case '"' -> out.append("&quot;");
        case '\'' -> out.append("&#39;");
        default -> out.append(c);
      }
    }
    return out.toString();
  }

  /**
   * Makes JSON safe inside {@code <script>}: no {@code </script>} or {@code <!--} can end the
   * element, and the line separators some parsers reject are escaped (007 R2.7). The result is
   * still the same JSON value.
   */
  public static String scriptJson(String json) {
    return json.replace("<", "\\u003c").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
  }
}
