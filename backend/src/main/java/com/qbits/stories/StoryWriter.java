package com.qbits.stories;

import com.qbits.stories.domain.StoryDraft;
import com.qbits.stories.domain.StoryInput;
import java.util.Optional;

/**
 * Writes a story draft from a headline and feed excerpt. The only way the app reaches the summary
 * service (conventions). Implementations must send nothing beyond {@link StoryInput}.
 */
public interface StoryWriter {

  /**
   * @param feedback rules the previous draft broke, when retrying (spec 002 R8.2)
   * @throws WriterUnavailable when the service is down, slow or rate-limited; try again later
   * @throws WriterRejected when the service refused this request; do not retry
   */
  StoryDraft write(StoryInput input, Optional<String> feedback);

  /** The service is temporarily unavailable (5xx, 429, timeout). */
  class WriterUnavailable extends RuntimeException {
    public WriterUnavailable(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /** The service rejected the request (4xx other than 429). */
  class WriterRejected extends RuntimeException {
    public WriterRejected(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
