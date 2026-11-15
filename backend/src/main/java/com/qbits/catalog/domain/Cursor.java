package com.qbits.catalog.domain;

import java.time.Instant;
import java.util.UUID;

/** Position in the newest-first story list: the last story the reader has seen. */
public record Cursor(Instant publishedAt, UUID id) {}
