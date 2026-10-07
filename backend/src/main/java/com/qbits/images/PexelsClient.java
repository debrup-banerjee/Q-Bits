package com.qbits.images;

import com.qbits.images.domain.PhotoCandidate;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Searches Pexels through its official API (spec 009 R2). Keeps at most {@code perHour} calls an
 * hour and a minimum gap between calls, inside Pexels' default limits. Only one instance runs the
 * image job at a time (database lock), so in-memory state is enough.
 */
@Component
public class PexelsClient {

  /** Where credits link to: Pexels asks apps to show a prominent link to it. */
  public static final String HOME = "https://www.pexels.com";

  private final RestClient http;
  private final ImagesProperties.Pexels props;
  private final JsonMapper json;
  private final Clock clock;
  private final Deque<Instant> calls = new ArrayDeque<>();

  public PexelsClient(
      @Qualifier("imagesRestClient") RestClient http,
      ImagesProperties props,
      JsonMapper json,
      Clock clock) {
    this.http = http;
    this.props = props.pexels();
    this.json = json;
    this.clock = clock;
  }

  /** Thrown when the search could not run; the job retries later. */
  public static class SearchFailed extends RuntimeException {
    public SearchFailed(String message) {
      super(message);
    }
  }

  public boolean configured() {
    return props.configured();
  }

  /** Takes one call from the hourly budget if allowed right now. */
  public synchronized boolean tryAcquire() {
    Instant now = clock.instant();
    while (!calls.isEmpty() && calls.peekFirst().isBefore(now.minus(Duration.ofHours(1)))) {
      calls.pollFirst();
    }
    if (calls.size() >= props.perHour()
        || (!calls.isEmpty() && calls.peekLast().plus(props.minSpacing()).isAfter(now))) {
      return false;
    }
    calls.addLast(now);
    return true;
  }

  /**
   * Landscape photos for the phrase. The caller must have taken a call from the budget with {@link
   * #tryAcquire()}. Uses Pexels' own landscape crop, hotlinked from its CDN.
   */
  public List<PhotoCandidate> search(String query) {
    URI uri =
        UriComponentsBuilder.fromUriString(props.baseUrl())
            .path("/v1/search")
            .queryParam("query", query)
            .queryParam("orientation", "landscape")
            .queryParam("per_page", 10)
            .encode()
            .build()
            .toUri();
    String body;
    try {
      body =
          http.get()
              .uri(uri)
              .header(HttpHeaders.AUTHORIZATION, props.apiKey())
              .retrieve()
              .body(String.class);
    } catch (RuntimeException e) {
      throw new SearchFailed("search: " + e.getClass().getSimpleName());
    }
    List<PhotoCandidate> out = new ArrayList<>();
    for (JsonNode p : json.readTree(body == null ? "{}" : body).path("photos")) {
      out.add(
          new PhotoCandidate(
              text(p.path("id")),
              text(p.at("/src/landscape")),
              text(p.path("url")),
              text(p.path("alt")),
              text(p.path("photographer")),
              text(p.path("photographer_url")),
              text(p.path("avg_color"))));
    }
    return out;
  }

  private static String text(JsonNode n) {
    return n == null || n.isMissingNode() || n.isNull() ? null : n.asString();
  }
}
