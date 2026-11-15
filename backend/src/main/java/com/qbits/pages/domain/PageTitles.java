package com.qbits.pages.domain;

/**
 * Page titles and fixed descriptions (spec 007 R2.1, R2.2). The web app sets the same titles during
 * navigation (R5.1); both sides are checked against {@code specs/007-seo-pages/titles.json}.
 */
public final class PageTitles {

  public static final String HOME = "Q-Bits: today's AI news, explained in plain words";
  public static final String SECTIONS = "AI news by section | Q-Bits";
  public static final String ABOUT = "About Q-Bits";
  public static final String LOGIN = "Log in | Q-Bits";
  public static final String REGISTER = "Create an account | Q-Bits";
  public static final String STORY_NOT_FOUND = "Story not available | Q-Bits";
  public static final String NOT_FOUND = "Page not found | Q-Bits";

  public static final String HOME_DESCRIPTION =
      "Today's AI news from around the world, explained in plain words, with the key terms and a"
          + " link to every original story.";
  public static final String SECTIONS_DESCRIPTION =
      "AI news from the last 72 hours, sorted into sections and explained in plain words.";
  public static final String ABOUT_DESCRIPTION =
      "How Q-Bits writes its plain-language AI news summaries, the sources it reads and how to"
          + " reach us.";
  public static final String NOT_FOUND_DESCRIPTION =
      "This page isn't available. Q-Bits keeps stories from the last 72 hours.";

  private PageTitles() {}

  public static String story(String headline) {
    return headline + " | Q-Bits";
  }

  public static String section(String sectionName) {
    return sectionName + ": AI news from the last 72 hours | Q-Bits";
  }
}
