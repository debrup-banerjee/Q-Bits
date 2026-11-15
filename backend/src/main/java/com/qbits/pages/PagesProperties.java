package com.qbits.pages;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Server-filled pages (spec 007).
 *
 * @param siteUrl canonical origin, e.g. {@code https://qbitsnews.com}
 * @param shellFile the web build's {@code index.html}
 */
@Validated
@ConfigurationProperties(prefix = "qbits.pages")
public record PagesProperties(@NotBlank String siteUrl, @NotBlank String shellFile) {}
