package com.qbits.common.links;

/**
 * A URL seen in a feed entry and where it was seen (spec 005 R1). Never carries surrounding text.
 * Shared value type: ingestion finds these links and the resources feature checks them, so it lives
 * in {@code common} and neither feature's domain depends on the other.
 */
public record FoundLink(String url, LinkOrigin origin) {}
