package com.qbits.auth;

import com.qbits.auth.domain.User;
import com.qbits.auth.persistence.UserRepository;
import com.qbits.common.AppException;
import com.qbits.common.Ids;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/** Registration, login and Google sign-in for optional accounts. Never required to browse. */
@Service
public class AuthService {

  /** A signed session token plus the username it belongs to, for the client to display. */
  public record Session(String token, String username) {}

  private final UserRepository users;
  private final PasswordPolicy policy;
  private final TokenService tokens;
  private final GoogleTokenVerifier google;
  private final Clock clock;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

  AuthService(
      UserRepository users,
      PasswordPolicy policy,
      TokenService tokens,
      GoogleTokenVerifier google,
      Clock clock) {
    this.users = users;
    this.policy = policy;
    this.tokens = tokens;
    this.google = google;
    this.clock = clock;
  }

  public Session register(String username, String password) {
    policy.validate(username, password);
    String trimmed = username.trim();
    if (users.findByUsername(trimmed).isPresent()) {
      throw new AppException("USERNAME_TAKEN", HttpStatus.CONFLICT, "That username is taken.");
    }
    User user = new User(Ids.newId(), trimmed, encoder.encode(password), null, clock.instant());
    users.save(user);
    return session(user);
  }

  public Session login(String username, String password) {
    User user =
        users
            .findByUsername(username == null ? "" : username.trim())
            .orElseThrow(AuthService::invalidCredentials);
    if (user.passwordHash() == null
        || !encoder.matches(password == null ? "" : password, user.passwordHash())) {
      throw invalidCredentials();
    }
    return session(user);
  }

  public Session google(String idToken) {
    GoogleTokenVerifier.GoogleIdentity identity = google.verify(idToken);
    User user =
        users
            .findByGoogleSubject(identity.subject())
            .orElseGet(
                () -> {
                  User created =
                      new User(
                          Ids.newId(),
                          usernameFor(identity),
                          null,
                          identity.subject(),
                          clock.instant());
                  users.save(created);
                  return created;
                });
    return session(user);
  }

  /** Looks up the current user from a bearer token, for the client to confirm it's still valid. */
  public String username(String token) {
    var userId = tokens.verify(token);
    if (userId.isEmpty()) {
      throw new AppException("INVALID_TOKEN", HttpStatus.UNAUTHORIZED, "Sign in again.");
    }
    return users
        .find(userId.get())
        .orElseThrow(
            () -> new AppException("INVALID_TOKEN", HttpStatus.UNAUTHORIZED, "Sign in again."))
        .username();
  }

  private Session session(User user) {
    return new Session(tokens.issue(user.id()), user.username());
  }

  private static AppException invalidCredentials() {
    return new AppException(
        "INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED, "Wrong username or password.");
  }

  /** A default username from the Google profile, deduplicated if another account already has it. */
  private String usernameFor(GoogleTokenVerifier.GoogleIdentity identity) {
    String local =
        identity.email() != null && identity.email().contains("@")
            ? identity.email().substring(0, identity.email().indexOf('@'))
            : "user";
    String base = local.replaceAll("[^A-Za-z0-9._-]", "");
    if (base.length() < PasswordPolicy.MIN_USERNAME) {
      base = (base + "user").substring(0, PasswordPolicy.MIN_USERNAME);
    }
    if (base.length() > PasswordPolicy.MAX_USERNAME) {
      base = base.substring(0, PasswordPolicy.MAX_USERNAME);
    }
    String candidate = base;
    for (int suffix = 1; users.findByUsername(candidate).isPresent(); suffix++) {
      String suffixed = base + suffix;
      candidate =
          suffixed.length() > PasswordPolicy.MAX_USERNAME
              ? suffixed.substring(suffixed.length() - PasswordPolicy.MAX_USERNAME)
              : suffixed;
    }
    return candidate;
  }
}
