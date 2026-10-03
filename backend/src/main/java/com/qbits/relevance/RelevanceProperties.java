package com.qbits.relevance;

import com.qbits.relevance.domain.Keyword;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Keyword list and threshold for the relevance pre-filter (spec 001 R5.2). */
@Validated
@ConfigurationProperties(prefix = "qbits.relevance")
public record RelevanceProperties(@Positive double threshold, @NotEmpty List<Keyword> keywords) {}
