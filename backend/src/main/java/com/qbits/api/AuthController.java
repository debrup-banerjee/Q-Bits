package com.qbits.api;

import com.qbits.auth.AuthService;
import com.qbits.auth.AuthService.Session;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Optional accounts: register, log in with a password, or sign in with Google. Every other endpoint
 * stays public; nothing here gates access to the news (conventions: auth is additive).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  public record Credentials(String username, String password) {}

  public record GoogleSignIn(String idToken) {}

  public record SessionView(String token, String username) {}

  private final AuthService auth;

  public AuthController(AuthService auth) {
    this.auth = auth;
  }

  @PostMapping("/register")
  public ResponseEntity<SessionView> register(@RequestBody Credentials body) {
    Session s = auth.register(body.username(), body.password());
    return ResponseEntity.status(HttpStatus.CREATED).body(view(s));
  }

  @PostMapping("/login")
  public ResponseEntity<SessionView> login(@RequestBody Credentials body) {
    return ResponseEntity.ok(view(auth.login(body.username(), body.password())));
  }

  @PostMapping("/google")
  public ResponseEntity<SessionView> google(@RequestBody GoogleSignIn body) {
    return ResponseEntity.ok(view(auth.google(body.idToken())));
  }

  /**
   * Confirms a stored token is still valid, so the client knows whether to show it as signed in.
   */
  @GetMapping("/me")
  public ResponseEntity<SessionView> me(
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    String token =
        authorization != null && authorization.startsWith("Bearer ")
            ? authorization.substring("Bearer ".length())
            : authorization;
    return ResponseEntity.ok(new SessionView(token, auth.username(token)));
  }

  private static SessionView view(Session s) {
    return new SessionView(s.token(), s.username());
  }
}
