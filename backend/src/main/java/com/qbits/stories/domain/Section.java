package com.qbits.stories.domain;

import java.util.Arrays;
import java.util.Optional;

/** The five reader-facing sections, in display order. Defined once here (conventions). */
public enum Section {
  GLOBAL_AI_TECH(
      "global-ai-tech",
      "AI Wire",
      "Ongoing AI tech coverage: capabilities, updates and what's changing."),
  NEW_RELEASES(
      "new-releases",
      "New Releases",
      "Brand-new model launches and major AI-relevant hardware, like a new GPU generation."),
  WORLD_BUSINESS(
      "world-business",
      "AI in Business",
      "Money, companies, chips and jobs around the world: where AI meets the economy."),
  INDIA_AI(
      "india-ai",
      "India AI",
      "Everything AI in India: companies, startups, government, research and global players' moves"
          + " in India."),
  INNOVATIONS_RESEARCH(
      "innovations-research",
      "AI Innovations",
      "New ideas and discoveries from labs and universities.");

  private final String slug;
  private final String displayName;
  private final String description;

  Section(String slug, String displayName, String description) {
    this.slug = slug;
    this.displayName = displayName;
    this.description = description;
  }

  public String slug() {
    return slug;
  }

  public String displayName() {
    return displayName;
  }

  public String description() {
    return description;
  }

  public static Optional<Section> fromSlug(String slug) {
    return Arrays.stream(values()).filter(s -> s.slug.equals(slug)).findFirst();
  }
}
