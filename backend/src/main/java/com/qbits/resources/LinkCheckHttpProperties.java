package com.qbits.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * HTTP settings for link checks (spec 005). By default the same timeout and User-Agent as feed
 * fetching, set in {@code application.yml}.
 */
@Validated
@ConfigurationProperties(prefix = "qbits.resources.http")
public record LinkCheckHttpProperties(@NotNull Duration timeout, @NotBlank String userAgent) {}
