package com.qbits.stories.digest;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Daily digest settings (spec 006). */
@Validated
@ConfigurationProperties(prefix = "qbits.digest")
public record DigestProperties(
    @NotNull LocalTime cutoffTime,
    @NotNull ZoneId zone,
    @NotNull Duration tick,
    @NotNull Duration pollEvery,
    @NotNull Duration submitRetryEvery,
    @NotNull Duration publishDeadline,
    @NotNull Duration lateNoticeAfter) {}
