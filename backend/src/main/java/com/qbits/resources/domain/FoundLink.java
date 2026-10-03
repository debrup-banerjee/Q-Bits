package com.qbits.resources.domain;

/** A URL seen in a feed entry and where it was seen. Never carries surrounding text. */
public record FoundLink(String url, LinkOrigin origin) {}
