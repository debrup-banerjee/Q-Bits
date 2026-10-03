package com.qbits.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Top-level application settings under the {@code qbits.*} prefix. */
@Validated
@ConfigurationProperties(prefix = "qbits")
public record QBitsProperties(@NotBlank String sourcesFile, @NotNull @Email String contactEmail) {}
