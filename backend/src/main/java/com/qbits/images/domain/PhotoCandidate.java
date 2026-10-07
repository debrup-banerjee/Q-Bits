package com.qbits.images.domain;

/**
 * One result from a photo library search. {@code imageUrl} is the library's own hotlink URL and
 * {@code pageUrl} the photo's page. Nothing else from the response is kept.
 */
public record PhotoCandidate(
    String id,
    String imageUrl,
    String pageUrl,
    String description,
    String photographer,
    String photographerUrl,
    String color) {}
