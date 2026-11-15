package com.qbits.relevance.domain;

/** A term that signals an AI story, with how strongly it counts. */
public record Keyword(String term, double weight) {}
