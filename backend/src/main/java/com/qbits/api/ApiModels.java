package com.qbits.api;

/** Response shapes that are not domain records. */
final class ApiModels {

  private ApiModels() {}

  /** A section with its story count in the last 72 hours (spec 003 R1.1). */
  record SectionView(String slug, String name, String description, long storyCount) {}

  /** An enabled source, name and homepage only (spec 003 R3.1). */
  record SourceView(String name, String homepage) {}

  /** Site details for the About page (spec 003 R7.3). */
  record SiteView(String name, String contactEmail) {}
}
