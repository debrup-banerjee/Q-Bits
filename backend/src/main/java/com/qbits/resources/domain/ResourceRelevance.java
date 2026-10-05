package com.qbits.resources.domain;

import com.qbits.common.links.LinkOrigin;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Keeps links from the entry's own links and description; keeps links found only in the content
 * element when the project's name shows up in the title or excerpt (spec 005 R3). This drops share
 * buttons, footers and "follow us" links. Pure.
 */
public final class ResourceRelevance {

  public boolean keep(Candidate c, LinkOrigin origin, String title, String excerpt) {
    if (origin != LinkOrigin.CONTENT) {
      return true;
    }
    String text =
        ((title == null ? "" : title) + " " + (excerpt == null ? "" : excerpt))
            .toLowerCase(Locale.ROOT);
    return Arrays.stream(c.name().split("[/\\-_.]+"))
        .map(w -> w.toLowerCase(Locale.ROOT))
        .filter(w -> w.length() >= 3 && w.chars().anyMatch(Character::isLetter))
        .anyMatch(
            w ->
                Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(w) + "(?![\\p{L}\\p{N}])")
                    .matcher(text)
                    .find());
  }
}
