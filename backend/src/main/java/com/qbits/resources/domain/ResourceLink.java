package com.qbits.resources.domain;

/**
 * A verified open-source link as clients see it (spec 005 R5). {@code type} is lower case: code,
 * model, dataset or paper.
 */
public record ResourceLink(String type, String label, String url, String name) {}
