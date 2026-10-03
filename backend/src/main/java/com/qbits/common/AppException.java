package com.qbits.common;

import org.springframework.http.HttpStatus;

/** A domain failure with a stable code that clients can rely on (conventions: error handling). */
public class AppException extends RuntimeException {

  private final String code;
  private final HttpStatus status;

  public AppException(String code, HttpStatus status, String detail) {
    super(detail);
    this.code = code;
    this.status = status;
  }

  public String code() {
    return code;
  }

  public HttpStatus status() {
    return status;
  }
}
