package com.qbits.auth;

import com.qbits.common.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Verifies a Google Sign-In ID token via Google's tokeninfo endpoint -- no JWT/JOSE library needed
 * for one check. QBITS_GOOGLE_CLIENT_ID must match the token's audience, so a token meant for a
 * different app is rejected.
 */
@Service
class GoogleTokenVerifier {

  private final RestClient http;
  private final AuthProperties props;

  GoogleTokenVerifier(RestClient.Builder builder, AuthProperties props) {
    this.http = builder.baseUrl("https://oauth2.googleapis.com").build();
    this.props = props;
  }

  boolean isConfigured() {
    return props.googleClientId() != null && !props.googleClientId().isBlank();
  }

  GoogleIdentity verify(String idToken) {
    if (!isConfigured()) {
      throw new AppException(
          "GOOGLE_SIGN_IN_UNAVAILABLE",
          HttpStatus.SERVICE_UNAVAILABLE,
          "Google sign-in is not configured.");
    }
    TokenInfo info;
    try {
      info =
          http.get()
              .uri(b -> b.path("/tokeninfo").queryParam("id_token", idToken).build())
              .retrieve()
              .body(TokenInfo.class);
    } catch (RestClientException e) {
      throw invalid();
    }
    if (info == null || info.sub() == null || !props.googleClientId().equals(info.aud())) {
      throw invalid();
    }
    return new GoogleIdentity(info.sub(), info.email());
  }

  private static AppException invalid() {
    return new AppException(
        "INVALID_GOOGLE_TOKEN", HttpStatus.UNAUTHORIZED, "Could not verify Google sign-in.");
  }

  private record TokenInfo(String sub, String aud, String email) {}

  record GoogleIdentity(String subject, String email) {}
}
