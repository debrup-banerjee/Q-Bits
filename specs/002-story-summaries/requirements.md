# 002 — Story summaries: Requirements

## Summary
Turn each candidate item from spec 001 into a reader-ready story: confirm it is really about AI, place it in one of the four sections, and write a plain-language headline, a short story-style summary and a few "key terms" explained in everyday words. Both non-technical and technical readers should understand it and want to come back. Everything is written in our own words from the publisher's headline and feed excerpt only, following `steering/editorial-style.md`.

## In scope
- Background job that processes `PENDING` items
- Final AI-relevance decision
- Section assignment (Global AI Tech, World Business, India AI, Innovations & Research)
- Plain headline, story summary, key terms
- Validation: length, originality, no invented numbers, required fields
- Cost and failure controls; traceability of how each story was written

## Out of scope
- Showing stories (003)
- Merging several outlets' coverage of the same event into one story (candidate for a later spec)
- Translation into Indian languages (later)

## Behaviours and acceptance criteria

### R1 — Processing pending items
- **R1.1** THE SYSTEM SHALL process `PENDING` items in the background, newest `published_at` first, without any reader action.
- **R1.2** THE SYSTEM SHALL send the summary service only: source title, source excerpt, source name, source region, section hint and publish date. Nothing else.
- **R1.3** THE SYSTEM SHALL make at most one successful summary call per item; a published story is never rewritten unless an operator asks.

### R2 — Final AI relevance
- **R2.1** WHEN the summary service judges an item not to be mainly about AI or technology that directly affects AI THE SYSTEM SHALL set status `NOT_AI` and never show it.
- **R2.2** THE SYSTEM SHALL record the one-line reason given for `NOT_AI`.

### R3 — Sections
- **R3.1** THE SYSTEM SHALL assign exactly one section to each published story.
- **R3.2** IF the story has a real India angle — an Indian company, startup, institution or government body; a global company's launch, investment, plant, hiring or pricing in India; Indian policy or regulation; Indian research — THEN the section SHALL be India AI, whatever the topic. A passing mention (India as one of many countries in a list) does not count.
- **R3.3** OTHERWISE IF the story is about research, a lab result or a not-yet-released innovation THEN the section SHALL be Innovations & Research.
- **R3.4** OTHERWISE IF the story is mainly about money, companies, deals, jobs, chips/plants, markets or regulation THEN the section SHALL be World Business.
- **R3.5** OTHERWISE the section SHALL be Global AI Tech (models, products, versions, capabilities, technical specs).
- **R3.6** The source's `sectionHint` SHALL be passed as a hint only; the rules above decide.

### R4 — Headline and summary
- **R4.1** THE SYSTEM SHALL write a headline of at most 12 words in plain language.
- **R4.2** THE SYSTEM SHALL write a summary of 80–120 words, or 40–120 words when the input excerpt is under 150 characters.
- **R4.3** The summary SHALL follow the shape: what happened → why it matters → who it affects or what to watch.
- **R4.4** The summary SHALL name the source (e.g. "according to TechCrunch").
- **R4.5** The summary SHALL keep any hedging in the input ("plans to", "says", "could").

### R5 — Key terms
- **R5.1** THE SYSTEM SHALL return 0–5 key terms, each with a term and a plain explanation of at most 20 words.
- **R5.2** Every important AI or tech term in the input SHALL appear in the summary, either explained in the sentence or listed as a key term.
- **R5.3** Each key term SHALL appear (case-insensitive) in the source title, the source excerpt or the summary.
- **R5.4** Acronyms SHALL be spelled out on first use in the summary.

### R6 — Accuracy guard
- **R6.1** Every number in the headline and summary (digits, with units) SHALL appear in the source title or excerpt; otherwise the output is invalid.
- **R6.2** Product and model names SHALL be written as in the input.

### R7 — Originality guard
- **R7.1** The summary SHALL share no run of 8 or more consecutive words with the source title or excerpt (case- and punctuation-insensitive), excluding product names.
- **R7.2** The summary SHALL contain no quotations from the source.

### R8 — Validation and retries
- **R8.1** THE SYSTEM SHALL validate every response (JSON shape, section value, lengths, R5.3, R6.1, R7) before saving.
- **R8.2** IF validation fails THEN THE SYSTEM SHALL retry once, telling the service which rule failed.
- **R8.3** IF the retry also fails THEN THE SYSTEM SHALL set status `REJECTED` with the failed rule, and never show it.
- **R8.4** Only items that pass validation SHALL get status `PUBLISHED`.

### R9 — Cost and failure control
- **R9.1** THE SYSTEM SHALL cap summary calls per day (config, default 500) and stop for the day when reached, leaving items `PENDING`.
- **R9.2** IF the service errors or is rate-limited THEN items SHALL stay `PENDING` and be retried with backoff; ingestion (001) SHALL be unaffected.
- **R9.3** THE SYSTEM SHALL skip `PENDING` items older than 72 hours, setting status `EXPIRED`.
- **R9.4** THE SYSTEM SHALL process at most 4 items in parallel (config).

### R10 — Traceability
- **R10.1** THE SYSTEM SHALL store with each story: model name, prompt version, written_at, input and output token counts, attempts.
- **R10.2** THE SYSTEM SHALL provide an operator command to re-write one story or all stories of a source with the current prompt.

## Non-functional
- A new item is normally published within 15 minutes of being ingested.
- The API key comes only from the `ANTHROPIC_API_KEY` environment variable.
- Tests use a fake writer; no test calls the real service except an opt-in live suite.

## Principles touched
Content and legal (summaries from title and excerpt only, own words, length, attribution, data sent to the service), Architecture (background writing, show only after validation, service outage tolerance).

## Open questions
- Daily call cap: is 500 right? At the expected 100–300 items a day it leaves headroom.
- Do you want a later spec to merge coverage of the same event from several outlets into one story with multiple "Read more" links?
