# 001 — Feed ingestion: Design

## Overview
A scheduled job in the Spring Boot backend walks the enabled sources, checks robots.txt, does a conditional GET of each feed, parses entries with Rome, normalises them, runs the keyword pre-filter, de-duplicates against the database and inserts new items with story status `PENDING`. Spec 002 picks up `PENDING` items. Each source is fetched on its own virtual thread so one slow or broken feed never holds up the rest.

## Components
| Component | Package | Responsibility | Requirements |
|---|---|---|---|
| `SourceRegistry` | `sources` | Load and validate `config/sources.yml` at startup; expose enabled sources. | R1 |
| `RobotsPolicy` | `ingestion` | Fetch, cache (24 h, in DB) and evaluate robots.txt with crawler-commons. | R3 |
| `FeedFetcher` | `ingestion` | Conditional GET with timeouts, retries, User-Agent; returns body or 304/failure. | R2, R7.1 |
| `FeedParser` | `ingestion` | Rome → list of raw entries. | R4 |
| `ItemNormaliser` | `ingestion.domain` | Pure: excerpt cleanup/truncation, canonical URL, date fallback, malformed check, 72 h cut-off. | R4, R9.1 |
| `RelevancePreFilter` | `relevance.domain` | Pure: weighted whole-word keyword score from title + excerpt. | R5 |
| `Deduplicator` | `ingestion` | Canonical-URL uniqueness + same-source title match in 48 h. | R6 |
| `IngestionJob` | `ingestion` | `@Scheduled` + ShedLock; per-source due check; fan-out; writes fetch log. | R2.1, R2.5, R7 |
| `SourceHealth` | `ingestion` | Consecutive-failure counter, `DEGRADED` flag, Actuator health contributor. | R7.4, R7.5 |
| `SourceAdminCommands` | `sources` | Disable handling and purge command (Spring Shell-free: a `@Profile("admin")` `CommandLineRunner` taking `--purge-source=<id>`). | R8 |
| `RetentionJob` | `ingestion` | Daily delete of items older than 7 days. | R9.2 |

## Data model (migration `V1__ingestion.sql`)
```sql
create table sources_state (
  source_id            text primary key,
  etag                 text,
  last_modified        text,
  last_fetched_at      timestamptz,
  last_success_at      timestamptz,
  consecutive_failures int  not null default 0,
  health               text not null default 'OK'          -- OK | DEGRADED
);

create table robots_cache (
  host        text primary key,
  body        text,
  status_code int not null,
  fetched_at  timestamptz not null
);

create table items (
  id               uuid primary key,                       -- UUIDv7
  source_id        text not null,
  canonical_url    text not null unique,
  source_title     text not null check (char_length(source_title) <= 500),
  source_excerpt   text check (char_length(source_excerpt) <= 300),
  title_key        text not null,                          -- lower-cased, punctuation-stripped title
  published_at     timestamptz not null,
  date_estimated   boolean not null default false,
  fetched_at       timestamptz not null,
  prefilter_score  numeric(4,3) not null,
  story_status     text not null default 'PENDING',        -- PENDING | PUBLISHED | NOT_AI | REJECTED (002)
  hidden           boolean not null default false
);
create index items_source_title_idx on items (source_id, title_key, published_at);
create index items_status_idx       on items (story_status, published_at);

create table source_fetch_log (
  id            bigserial primary key,
  source_id     text not null,
  started_at    timestamptz not null,
  duration_ms   int not null,
  status        text not null,   -- OK | NOT_MODIFIED | BLOCKED_BY_ROBOTS | ROBOTS_UNAVAILABLE | FAILED
  http_status   int,
  items_new     int not null default 0,
  items_dup     int not null default 0,
  items_not_ai  int not null default 0,
  items_bad     int not null default 0,
  items_too_old int not null default 0,
  error         text
);
create table shedlock (name varchar(64) primary key, lock_until timestamp not null,
  locked_at timestamp not null, locked_by varchar(255) not null);
```
No column can hold an article body: `source_excerpt` is capped at 300 characters by a check constraint.

## Contracts
**Config (`application.yml`)**
```yaml
qbits:
  sources-file: config/sources.yml
  ingestion:
    tick: PT5M                 # how often the job wakes to look for due sources
    default-interval: PT30M
    min-interval: PT15M
    timeout: PT10S
    retries: 2
    max-age: PT72H             # ignore older entries
    retention: P7D
    user-agent: "QBits/${app.version} (+mailto:debrup28.nitdgp@gmail.com)"
  contact-email: debrup28.nitdgp@gmail.com
  relevance:
    threshold: 1.0
    keywords: { "artificial intelligence": 1.0, "AI": 0.6, "machine learning": 1.0,
                "large language model": 1.0, "LLM": 1.0, "chatbot": 0.6, "GPU": 0.5,
                "semiconductor": 0.5, "neural network": 1.0, "generative": 0.6, "OpenAI": 1.0,
                "Gemini": 0.8, "DeepMind": 1.0, "Anthropic": 1.0, "NVIDIA": 0.6, "IndiaAI": 1.0 }
```
**Health:** `GET /actuator/health` → component `sources` with per-source `{ status, lastSuccessAt, consecutiveFailures }`.

## Flow
1. `IngestionJob` wakes every `tick`, takes the ShedLock, selects enabled sources whose `last_fetched_at + interval` has passed.
2. Per source, on a virtual thread: `RobotsPolicy.allowed(feedUrl)` → if disallowed, log `BLOCKED_BY_ROBOTS`; if robots unavailable (5xx/timeout), log `ROBOTS_UNAVAILABLE` and skip. Crawl-delay above the interval replaces the interval.
3. `FeedFetcher` sends conditional GET. 304 → log `NOT_MODIFIED`. Failure after retries → log `FAILED`, bump failures.
4. `FeedParser` → raw entries → `ItemNormaliser` (drop malformed, drop older than 72 h) → `RelevancePreFilter` (skip for `aiNative`) → `Deduplicator` → insert with `ON CONFLICT (canonical_url) DO NOTHING`.
5. Update `sources_state` (ETag, Last-Modified, success time, reset failures) and write the fetch log row.

## Error handling
Every per-source step is wrapped so any exception becomes a `FAILED` log row with a short error; the run continues. Five consecutive failures → `DEGRADED` + WARN log. Config errors fail startup with the entry id and field.

## Testing approach
- `ItemNormaliser`, `RelevancePreFilter`: table-driven unit tests (HTML stripping, word-boundary cut, `utm_*` removal, "AI" vs "said"/"Thailand").
- `FeedFetcher`, `RobotsPolicy`, `IngestionJob`: WireMock serving fixture feeds and robots files; Testcontainers Postgres.
- Idempotency: run the job twice on the same fixture → no new rows (R6.3).
- Failure isolation: one WireMock source returns 500, another succeeds → second still ingested (R7.3).
- Two-instance check: two job beans against one DB with ShedLock → each source fetched once (R2.5).

## Alternatives considered
- Separate ingestion service — not needed at this scale; principles say one deployable.
- Quartz — ShedLock + `@Scheduled` is enough.
- Storing robots.txt in memory — lost on restart and not shared between instances.

## Risks
- Feed URLs in `config/sources.yml` are unverified; some may have moved.
- Keyword pre-filter may drop genuine AI stories from general feeds; tune with the fetch-log counts.
