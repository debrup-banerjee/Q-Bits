# 001 — Feed ingestion: Requirements

## Summary
Pull new items from an approved list of well-known news outlets and official AI blogs on a schedule, keep only likely AI-related items, clean them up, remove duplicates and store them as candidates for the summary step (spec 002). Nothing from this spec is shown to readers directly. The only readers in v1 are the owner and his team.

## In scope
- Source registry loaded from `config/sources.yml`
- Scheduled, polite fetching of RSS and Atom feeds
- robots.txt checks
- Normalising items to the stored metadata fields
- A cheap AI-relevance pre-filter on title and excerpt
- Retention: deleting stored items after 7 days
- De-duplication
- Per-source health tracking
- Disabling and purging a source

## Out of scope
- Summaries, sections and key terms (002)
- News API and web reader, including the 72-hour window (003)
- News APIs that need keys (a later spec)
- User accounts

## Behaviours and acceptance criteria

### R1 — Source registry
As the operator, I want the sources defined in one reviewed file, so that only approved sources are ever fetched.

- **R1.1** WHEN the app starts THE SYSTEM SHALL load sources from `config/sources.yml`, each with: `id`, `name`, `homepage`, `feedUrl`, `type` (`rss` | `atom`), `termsUrl`, `termsReviewedOn`, `enabled`, `aiNative` (true if the source covers only AI), `region` (`global` | `india`) and optional `sectionHint`.
- **R1.2** IF any entry is missing a required field, has a duplicate `id`, or has an invalid URL THEN THE SYSTEM SHALL refuse to start and name the entry and field.
- **R1.4** IF an entry has `enabled: true` but no `termsUrl` or `termsReviewedOn` THEN THE SYSTEM SHALL refuse to start and name the entry.
- **R1.3** THE SYSTEM SHALL NOT fetch any URL not derived from a registered `feedUrl` or its host's `/robots.txt`.

### R2 — Scheduled, polite fetching
As a publisher, I want requests to be infrequent and cache-aware, so that the app costs me almost nothing.

- **R2.1** THE SYSTEM SHALL fetch each enabled source every 30 minutes by default, with an optional per-source interval no lower than 15 minutes.
- **R2.2** WHEN a source has a stored ETag or Last-Modified value THE SYSTEM SHALL send a conditional GET.
- **R2.3** WHEN the response is 304 Not Modified THE SYSTEM SHALL record the fetch and store no items.
- **R2.4** THE SYSTEM SHALL send the User-Agent `QBits/<version> (+mailto:debrup28.nitdgp@gmail.com)`.
- **R2.5** IF two app instances run THEN each source SHALL still be fetched at most once per interval.

### R3 — robots.txt
- **R3.1** WHEN fetching a source THE SYSTEM SHALL first check the host's robots.txt (cached up to 24 hours) for the feed path.
- **R3.2** IF the feed path is disallowed THEN THE SYSTEM SHALL skip the source and record status `BLOCKED_BY_ROBOTS`.
- **R3.3** IF robots.txt sets a Crawl-delay longer than the fetch interval THEN THE SYSTEM SHALL use the Crawl-delay as the interval.
- **R3.4** IF robots.txt returns 404 THEN THE SYSTEM SHALL treat fetching as allowed; IF it returns 5xx or times out THEN THE SYSTEM SHALL skip the source for this cycle.

### R4 — Normalising items
- **R4.1** THE SYSTEM SHALL store per item only: title, excerpt, canonical URL, source id, published_at, fetched_at, pre-filter score, and story status `PENDING` (the hand-off to spec 002).
- **R4.2** THE SYSTEM SHALL build the excerpt from the feed's description or summary only, strip all HTML, collapse whitespace, and cut it at a word boundary to at most 300 characters, adding `…` if cut.
- **R4.3** THE SYSTEM SHALL build the canonical URL by removing `utm_*`, `fbclid`, `gclid` and fragments, and lower-casing the host.
- **R4.4** IF an item has no publish date THEN THE SYSTEM SHALL use fetched_at and mark the item `dateEstimated = true`.
- **R4.5** IF an item has no title or no link THEN THE SYSTEM SHALL drop it and count it as malformed.

### R5 — AI relevance pre-filter
As the operator, I want obviously non-AI items dropped before the summary step, so that we don't pay to process them. (Spec 002 makes the final relevance call.)

- **R5.1** WHEN the source is `aiNative` THE SYSTEM SHALL keep every item, with score 1.0.
- **R5.2** WHEN the source is not `aiNative` THE SYSTEM SHALL score the item from title and excerpt using the weighted keyword list in config, and keep it only if the score meets the configured threshold.
- **R5.3** THE SYSTEM SHALL match keywords on whole words, case-insensitively, so that "AI" matches "AI chips" but not "said" or "Thailand".
- **R5.4** THE SYSTEM SHALL NOT use any article content beyond title and excerpt for scoring.
- **R5.5** THE SYSTEM SHALL record the count of items discarded as not relevant per fetch.

### R6 — De-duplication
- **R6.1** THE SYSTEM SHALL store at most one item per canonical URL.
- **R6.2** WHEN an item from the same source has the same normalised title as one stored in the last 48 hours THE SYSTEM SHALL treat it as a duplicate.
- **R6.3** Re-running ingestion on the same feed content SHALL create no new items.

### R7 — Failure isolation and health
- **R7.1** THE SYSTEM SHALL time out a fetch after 10 seconds and retry up to 2 times with backoff for timeouts and 5xx.
- **R7.2** THE SYSTEM SHALL record each fetch in `source_fetch_log` with status, HTTP code, duration, items new / duplicate / discarded / malformed.
- **R7.3** IF one source fails THEN other sources in the same run SHALL be unaffected.
- **R7.4** WHEN a source fails 5 times in a row THE SYSTEM SHALL mark it `DEGRADED` and log at WARN; one success clears it.
- **R7.5** THE SYSTEM SHALL expose per-source last success time and status through an Actuator health detail.

### R8 — Disable and purge
- **R8.1** WHEN a source is set to `enabled: false` THE SYSTEM SHALL stop fetching it from the next cycle and mark its stored items hidden.
- **R8.2** THE SYSTEM SHALL provide an operator command to purge all items for a source id, which completes within 24 hours of the request (target: immediately).

### R9 — Retention
- **R9.1** THE SYSTEM SHALL ignore feed entries published more than 72 hours before fetch time (they could never be shown).
- **R9.2** THE SYSTEM SHALL delete stored items, with their summaries, 7 days after published_at, in a daily job.

## Non-functional
- A full run over 50 sources finishes in under 2 minutes on a laptop.
- Tests never touch the network.
- The `items` table has no column able to hold article body text (no unbounded text besides excerpt, which is capped at 300 characters by a database check).

## Principles touched
Content and legal (all), Fetching (all), Architecture: idempotent ingestion, failure isolation, stateless instances.

## Open questions
- Initial source list: a 24-source candidate list is in `config/sources.yml`, all disabled until each feed URL and its terms are checked. Which should be enabled first?
- Relevance threshold and keyword weights — start with a list and tune on a week of data?
- Keep discarded items' URLs (not text) for 7 days to tune the filter, or count only?
