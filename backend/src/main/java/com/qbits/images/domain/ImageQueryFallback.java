package com.qbits.images.domain;

import com.qbits.stories.domain.KeyTerm;
import com.qbits.stories.domain.Section;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A photo search phrase built without the model (spec 009 R2.3): a known visual for the story's
 * first matching key term, else a neutral scene for its section. Never names a company, product or
 * person, so a search can never return a misleading picture of them.
 */
public final class ImageQueryFallback {

  /** Key words whose everyday visual is safe and recognisable. Checked in this order. */
  private static final List<Map.Entry<String, String>> VISUALS =
      List.of(
          Map.entry("gpu", "computer chip circuit board"),
          Map.entry("chip", "computer chip circuit board"),
          Map.entry("semiconductor", "semiconductor wafer"),
          Map.entry("data cent", "data center server racks"),
          Map.entry("robot", "industrial robot arm"),
          Map.entry("drone", "drone in the sky"),
          Map.entry("self-driving", "car on a highway at night"),
          Map.entry("autonomous", "car on a highway at night"),
          Map.entry("health", "hospital medical technology"),
          Map.entry("medical", "hospital medical technology"),
          Map.entry("education", "students studying with laptops"),
          Map.entry("security", "padlock on a keyboard"),
          Map.entry("deepfake", "camera lens close up"),
          Map.entry("voice", "microphone sound waves"),
          Map.entry("speech", "microphone sound waves"),
          Map.entry("image", "camera lens close up"),
          Map.entry("video", "film camera"),
          Map.entry("energy", "power lines at sunset"),
          Map.entry("funding", "city skyline office towers"),
          Map.entry("invest", "city skyline office towers"),
          Map.entry("regulation", "government building columns"),
          Map.entry("policy", "government building columns"),
          Map.entry("law", "courthouse columns"),
          Map.entry("research", "science laboratory"),
          Map.entry("quantum", "abstract light particles"),
          Map.entry("code", "code on a laptop screen"),
          Map.entry("open source", "code on a laptop screen"),
          Map.entry("model", "abstract neural network lights"));

  private static final Map<Section, String> BY_SECTION =
      Map.of(
          Section.GLOBAL_AI_TECH, "abstract technology network",
          Section.NEW_RELEASES, "laptop on a desk with soft light",
          Section.WORLD_BUSINESS, "city skyline office towers",
          Section.INDIA_AI, "bangalore city skyline",
          Section.INNOVATIONS_RESEARCH, "science laboratory");

  private ImageQueryFallback() {}

  public static String queryFor(Section section, List<KeyTerm> keyTerms) {
    for (KeyTerm term : keyTerms) {
      Optional<String> visual = visual(term.term());
      if (visual.isPresent()) {
        return visual.get();
      }
    }
    return BY_SECTION.getOrDefault(section, "abstract technology network");
  }

  private static Optional<String> visual(String term) {
    if (term == null) {
      return Optional.empty();
    }
    String lower = term.toLowerCase(Locale.ROOT);
    return VISUALS.stream()
        .filter(v -> lower.contains(v.getKey()))
        .map(Map.Entry::getValue)
        .findFirst();
  }
}
