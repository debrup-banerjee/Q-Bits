package com.qbits.sources.domain;

/** How a source is read. Only feed formats are allowed (principles: no page scraping). */
public enum SourceType {
  RSS,
  ATOM
}
