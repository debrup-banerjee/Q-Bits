package com.qbits.images;

import com.qbits.stories.domain.KeyTerm;
import java.util.List;
import java.util.Optional;

/**
 * Turns a story's own headline, summary and key terms into a short photo search phrase (spec 009
 * R2.1). Only Q-Bits' own text is sent, never the publisher's. Empty when no phrase could be had;
 * the job then falls back to a phrase built from the key terms.
 */
public interface ImageQueryWriter {

  Optional<String> phraseFor(String headline, String summary, List<KeyTerm> keyTerms);
}
