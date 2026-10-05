package com.qbits.auth.domain;

import java.time.Instant;
import java.util.UUID;

/** An account: either passwordHash or googleSubject is set, possibly both. */
public record User(
    UUID id, String username, String passwordHash, String googleSubject, Instant createdAt) {}
