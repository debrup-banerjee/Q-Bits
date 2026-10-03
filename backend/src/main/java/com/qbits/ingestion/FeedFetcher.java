package com.qbits.ingestion;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Fetches a feed politely: conditional GET with stored validators, timeouts, and a small number of
 * retries with exponential backoff for timeouts and server errors (spec 001 R2.2–R2.4, R7.1).
 */
@Service
public class FeedFetcher {

  private static final Logger log = LoggerFactory.getLogger(FeedFetcher.class);
  static final int MAX_BODY_BYTES = 5 * 1024 * 1024;

  private final RestClient http;
  private final IngestionProperties props;

  public FeedFetcher(RestClient feedRestClient, IngestionProperties props) {
    this.http = feedRestClient;
    this.props = props;
  }

  public FetchOutcome fetch(URI feedUrl, String etag, String lastModified) {
    FetchOutcome outcome = null;
    for (int attempt = 0; attempt <= props.retries(); attempt++) {
      if (attempt > 0) {
        sleep(props.retryBackoff().multipliedBy(1L << (attempt - 1)));
      }
      outcome = attempt(feedUrl, etag, lastModified);
      if (!isRetryable(outcome)) {
        return outcome;
      }
      log.info("feed retry url={} attempt={} reason={}", feedUrl, attempt + 1, describe(outcome));
    }
    return outcome;
  }

  private FetchOutcome attempt(URI feedUrl, String etag, String lastModified) {
    try {
      return http.get()
          .uri(feedUrl)
          .headers(
              h -> {
                h.set(
                    HttpHeaders.ACCEPT,
                    "application/rss+xml, application/atom+xml, application/xml;q=0.9, */*;q=0.5");
                if (etag != null) {
                  h.set(HttpHeaders.IF_NONE_MATCH, etag);
                }
                if (lastModified != null) {
                  h.set(HttpHeaders.IF_MODIFIED_SINCE, lastModified);
                }
              })
          .exchange(
              (request, response) -> {
                int status = response.getStatusCode().value();
                if (status == 304) {
                  return new FetchOutcome.NotModified();
                }
                if (status != 200) {
                  return new FetchOutcome.Failed(status, "HTTP " + status);
                }
                byte[] body = readLimited(response.getBody());
                HttpHeaders headers = response.getHeaders();
                return new FetchOutcome.Fetched(
                    status, body, headers.getETag(), headers.getFirst(HttpHeaders.LAST_MODIFIED));
              });
    } catch (RuntimeException e) {
      String reason =
          e.getCause() != null
              ? e.getCause().getClass().getSimpleName()
              : e.getClass().getSimpleName();
      return new FetchOutcome.Failed(null, "network: " + reason);
    }
  }

  private static byte[] readLimited(InputStream in) throws IOException {
    byte[] body = in.readNBytes(MAX_BODY_BYTES + 1);
    if (body.length > MAX_BODY_BYTES) {
      throw new IOException("feed larger than " + MAX_BODY_BYTES + " bytes");
    }
    return body;
  }

  private static boolean isRetryable(FetchOutcome outcome) {
    return outcome instanceof FetchOutcome.Failed f
        && (f.httpStatus() == null || f.httpStatus() >= 500 || f.httpStatus() == 429);
  }

  private static String describe(FetchOutcome outcome) {
    return outcome instanceof FetchOutcome.Failed f
        ? f.error()
        : outcome.getClass().getSimpleName();
  }

  private static void sleep(Duration duration) {
    try {
      Thread.sleep(duration);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while backing off", e);
    }
  }
}
