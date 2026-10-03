package com.qbits.api;

import com.qbits.common.AppException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Maps failures to RFC 9457 Problem Details with a {@code code} field (conventions). */
@RestControllerAdvice
class ProblemAdvice {

  private static final Logger log = LoggerFactory.getLogger(ProblemAdvice.class);

  @ExceptionHandler(AppException.class)
  ProblemDetail app(AppException e) {
    return problem(e.status(), e.code(), e.getMessage());
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ProblemDetail badParameter(MethodArgumentTypeMismatchException e) {
    return problem(
        HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Invalid value for '" + e.getName() + "'.");
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ProblemDetail notFound(NoResourceFoundException e) {
    return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", "No such endpoint.");
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail unexpected(Exception e) {
    String correlationId = UUID.randomUUID().toString();
    log.error("unexpected error correlationId={}", correlationId, e);
    ProblemDetail p =
        problem(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL",
            "Something went wrong. Please try again.");
    p.setProperty("correlationId", correlationId);
    return p;
  }

  private static ProblemDetail problem(HttpStatus status, String code, String detail) {
    ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
    p.setTitle(status.getReasonPhrase());
    p.setProperty("code", code);
    return p;
  }
}
