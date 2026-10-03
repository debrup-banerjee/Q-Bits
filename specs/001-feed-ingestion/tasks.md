# 001 — Feed ingestion: Tasks

Each task is 2–4 hours, leaves the full check green, and can be tested on its own.

## T1 — Backend skeleton
- **Covers:** groundwork for all
- **Depends on:** none
- **Do:** Maven Spring Boot 4 project in `backend/` (Java 21), Spotless, Flyway, Spring Data JDBC, Testcontainers, `docker-compose.yml` with Postgres 16, `qbits.*` config record, Actuator.
- **Tests:** `ApplicationStartsIT` (context loads against Testcontainers Postgres).
- **Done when:** `mvn -f backend/pom.xml verify` passes from a clean clone.
- **Status:** done

## T2 — Source registry
- **Covers:** R1.1, R1.2, R1.4
- **Depends on:** T1
- **Do:** `SourceRegistry` loading `config/sources.yml` into records with validation.
- **Tests:** `SourceRegistryTest`: valid file loads; missing field, duplicate id, bad URL, enabled-without-terms each fail with entry id and field.
- **Done when:** app refuses to start on each bad fixture with a clear message.

## T3 — Schema
- **Covers:** R4.1, R7.2, non-functional (no body column)
- **Depends on:** T1
- **Do:** `V1__ingestion.sql` as in design; repositories for `items`, `sources_state`, `source_fetch_log`, `robots_cache`.
- **Tests:** `ItemRepositoryIT` (insert/find, unique URL, excerpt > 300 chars rejected by DB).
- **Done when:** migration applies cleanly; constraints proven by tests.

## T4 — Item normaliser
- **Covers:** R4.2, R4.3, R4.4, R4.5, R9.1
- **Depends on:** T1
- **Do:** Pure `ItemNormaliser` with Jsoup excerpt cleanup, word-boundary truncation, canonical URL, date fallback, malformed and too-old checks.
- **Tests:** `ItemNormaliserTest` (table-driven, one case per criterion).
- **Done when:** all cases pass; no Spring dependency in the class.

## T5 — Relevance pre-filter
- **Covers:** R5.1–R5.5
- **Depends on:** T1
- **Do:** Pure `RelevancePreFilter` with configurable weighted keywords and whole-word, case-insensitive matching.
- **Tests:** `RelevancePreFilterTest` ("AI chips" matches; "said", "Thailand" don't; aiNative → 1.0; threshold edge).
- **Done when:** tests pass; keyword list read from config.

## T6 — robots.txt policy
- **Covers:** R3.1–R3.4
- **Depends on:** T3
- **Do:** `RobotsPolicy` with crawler-commons, DB cache 24 h, Crawl-delay handling, 404/5xx rules.
- **Tests:** `RobotsPolicyIT` with WireMock: allow, disallow, crawl-delay, 404, 500, cache hit.
- **Done when:** all six cases pass.

## T7 — Feed fetcher and parser
- **Covers:** R2.2, R2.3, R2.4, R7.1
- **Depends on:** T3
- **Do:** `FeedFetcher` (RestClient, timeout, retries with backoff, User-Agent, ETag/Last-Modified) and `FeedParser` (Rome).
- **Tests:** `FeedFetcherIT` (WireMock: 200, 304 with conditional headers asserted, timeout then success, 500 ×3 → failure, UA header); `FeedParserTest` on RSS and Atom fixtures.
- **Done when:** tests pass with no network access.

## T8 — De-duplication and ingestion job
- **Covers:** R2.1, R2.5, R6.1–R6.3, R7.2, R7.3
- **Depends on:** T2, T4, T5, T6, T7
- **Do:** `Deduplicator`, `IngestionJob` with due-source selection, virtual-thread fan-out, ShedLock, fetch-log writes; items inserted as `PENDING`.
- **Tests:** `IngestionJobIT`: end-to-end on fixtures; re-run creates nothing; failing source isolated; two job instances fetch each source once.
- **Done when:** a fixture run produces the expected rows and log counts.

## T9 — Health, disable, purge, retention
- **Covers:** R7.4, R7.5, R8.1, R8.2, R9.2
- **Depends on:** T8
- **Do:** `SourceHealth` + Actuator contributor; disabled sources skipped and items hidden; purge command; `RetentionJob`.
- **Tests:** `SourceHealthIT` (5 failures → DEGRADED, success clears), `SourceAdminIT` (disable hides, purge deletes), `RetentionJobIT` (7-day boundary with fixed `Clock`).
- **Done when:** all pass; `/actuator/health` shows per-source detail.
