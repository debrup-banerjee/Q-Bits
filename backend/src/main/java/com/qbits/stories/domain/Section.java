package com.qbits.stories.domain;

import java.util.Arrays;
import java.util.Optional;

/** The four reader-facing sections, in display order. Defined once here (conventions). */
public enum Section {
  GLOBAL_AI_TECH(
      "global-ai-tech", "Global AI Tech", "New AI models, products and what they can do."),
  WORLD_BUSINESS(
      "world-business",
      "World Business",
      "Money, companies, chips and jobs around the world: where AI meets the economy."),
  INDIA_AI(
      "india-ai",
      "India AI",
      "Everything AI in India: companies, startups, government, research and global players' moves"
          + " in India."),
  INNOVATIONS_RESEARCH(
      "innovations-research",
      "Innovations & Research",
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
