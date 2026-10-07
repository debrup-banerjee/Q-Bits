package com.qbits.pages.domain;

import java.util.List;

/**
 * Everything one server-filled page needs (spec 007 R1–R4).
 *
 * @param status HTTP status
 * @param title page title, unescaped
 * @param description meta description, unescaped
 * @param canonicalPath path on the site origin, e.g. {@code /story/…}; empty for pages that have no
 *     canonical URL (not-found pages)
 * @param ogType {@code article} or {@code website}
 * @param noindex true to ask search engines not to index the page
 * @param jsonLd JSON-LD documents, already serialised
 * @param body HTML placed inside {@code <div id="root">}, already escaped
 * @param initialData serialised initial data for the web app; empty for none
 * @param imageUrl the page's picture for link previews (spec 009 R6); empty for none
 */
public record PageModel(
    int status,
    String title,
    String description,
    String canonicalPath,
    String ogType,
    boolean noindex,
    List<String> jsonLd,
    String body,
    String initialData,
    String imageUrl) {

  public PageModel {
    jsonLd = List.copyOf(jsonLd);
    imageUrl = imageUrl == null ? "" : imageUrl;
  }

  public PageModel(
      int status,
      String title,
      String description,
      String canonicalPath,
      String ogType,
      boolean noindex,
      List<String> jsonLd,
      String body,
      String initialData) {
    this(status, title, description, canonicalPath, ogType, noindex, jsonLd, body, initialData, "");
  }

  /** The same page with a picture for link previews. */
  public PageModel withImage(String url) {
    return new PageModel(
        status, title, description, canonicalPath, ogType, noindex, jsonLd, body, initialData, url);
  }

  /** An indexable page with content. */
  public static PageModel page(
      String title,
      String description,
      String canonicalPath,
      String ogType,
      List<String> jsonLd,
      String body,
      String initialData) {
    return new PageModel(
        200, title, description, canonicalPath, ogType, false, jsonLd, body, initialData);
  }

  /** A page the web app renders on its own; search engines are asked not to index it. */
  public static PageModel appOnly(int status, String title, String description) {
    return new PageModel(status, title, description, "", "website", true, List.of(), "", "");
  }
}
