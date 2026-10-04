package com.qbits.auth;

import com.qbits.common.AppException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Signs and verifies session tokens: base64url(payload JSON) + "." + base64url(HMAC-SHA256 of that
 * string). No external JWT library needed for one claim. The secret comes only from
 * QBITS_AUTH_SECRET (see {@link AuthProperties}).
 */
@Service
class TokenService {

  private static final Duration TTL = Duration.ofDays(30);
  private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DEC = Base64.getUrlDecoder();

  private final byte[] secret;
  private final JsonMapper json;
  private final Clock clock;

  TokenService(AuthProperties props, JsonMapper json, Clock clock) {
    String s = props.secret();
    this.secret = s == null || s.isBlank() ? null : s.getBytes(StandardCharsets.UTF_8);
    this.json = json;
    this.clock = clock;
  }

  boolean isConfigured() {
    return secret != null;
  }

  String issue(UUID userId) {
    requireConfigured();
    Payload payload = new Payload(userId.toString(), clock.instant().plus(TTL).getEpochSecond());
    String encodedPayload = ENC.encodeToString(json.writeValueAsBytes(payload));
    return encodedPayload + "." + ENC.encodeToString(sign(encodedPayload));
  }

  Optional<UUID> verify(String token) {
    if (!isConfigured() || token == null) {
      return Optional.empty();
    }
    int dot = token.indexOf('.');
    if (dot < 0) {
      return Optional.empty();
    }
    String encodedPayload = token.substring(0, dot);
    byte[] givenSignature;
    try {
      givenSignature = DEC.decode(token.substring(dot + 1));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
    if (!MessageDigest.isEqual(givenSignature, sign(encodedPayload))) {
      return Optional.empty();
    }
    Payload payload;
    try {
      payload = json.readValue(DEC.decode(encodedPayload), Payload.class);
    } catch (RuntimeException e) {
      return Optional.empty();
    }
    if (clock.instant().getEpochSecond() > payload.exp()) {
      return Optional.empty();
    }
    try {
      return Optional.of(UUID.fromString(payload.sub()));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private byte[] sign(String encodedPayload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  private void requireConfigured() {
    if (!isConfigured()) {
      throw new AppException(
          "AUTH_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, "Accounts are not configured yet.");
    }
  }

  private record Payload(String sub, long exp) {}
}
