package com.qbits.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for optional accounts (conventions: auth is additive, never required to browse). The
 * secret signs session tokens and comes only from QBITS_AUTH_SECRET; without it, registration and
 * login answer AUTH_UNAVAILABLE but the rest of the app still runs. googleClientId enables Google
 * sign-in; without it, that endpoint answers GOOGLE_SIGN_IN_UNAVAILABLE.
 */
@ConfigurationProperties(prefix = "qbits.auth")
public record AuthProperties(String secret, String googleClientId) {}
