package com.qbits.resources.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A verified open-source link as clients see it (spec 005 R5). {@code type} is lower case: code,
 * model, dataset or paper (the API values of {@link ResourceType}, conventions).
 */
public record ResourceLink(
    @Schema(allowableValues = {"code", "model", "dataset", "paper"}) String type,
    String label,
    String url,
    String name) {}
