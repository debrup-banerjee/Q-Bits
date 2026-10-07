package com.qbits.images.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A story's picture as clients see it (spec 009 R5). {@code kind} is {@code photo} (a free photo
 * library, hotlinked as its licence asks) or {@code publisher} (a source's own feed image, shown
 * only with recorded permission). Clients always show the credit with the image, and label a photo
 * as illustrative: it is never a picture of the event itself. Stories without one get cover art
 * drawn by the client.
 */
public record StoryImage(
    @Schema(allowableValues = {"photo", "publisher"}) String kind,
    String url,
    String alt,
    String credit,
    @Schema(types = {"string", "null"}) String creditUrl,
    String provider,
    @Schema(types = {"string", "null"}) String providerUrl,
    @Schema(types = {"string", "null"}) String color) {}
