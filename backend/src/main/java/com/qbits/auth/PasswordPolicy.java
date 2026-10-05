package com.qbits.auth;

import com.qbits.common.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Username and password rules for registration. Kept deliberately simple at the owner's request: a
 * password needs only 5+ letters and digits, no special characters.
 */
@Component
class PasswordPolicy {

  static final int MIN_USERNAME = 3;
  static final int MAX_USERNAME = 30;
  static final int MIN_PASSWORD = 5;

  void validate(String username, String password) {
    String trimmed = username == null ? "" : username.trim();
    if (trimmed.length() < MIN_USERNAME || trimmed.length() > MAX_USERNAME) {
      throw new AppException(
          "INVALID_USERNAME",
          HttpStatus.BAD_REQUEST,
          "Username must be " + MIN_USERNAME + "-" + MAX_USERNAME + " characters.");
    }
    if (password == null || password.length() < MIN_PASSWORD) {
      throw new AppException(
          "WEAK_PASSWORD",
          HttpStatus.BAD_REQUEST,
          "Password must be at least " + MIN_PASSWORD + " characters.");
    }
    if (!password.chars().allMatch(Character::isLetterOrDigit)) {
      throw new AppException(
          "WEAK_PASSWORD",
          HttpStatus.BAD_REQUEST,
          "Password can only contain letters and numbers.");
    }
  }
}
