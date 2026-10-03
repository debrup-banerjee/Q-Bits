# 005 — Open-source links: Tasks

Each task is 2–4 hours, leaves the full check green, and can be tested on its own. Build after spec 004 (T7 uses its feed card).

## T1 — Schema and domain types
- **Covers:** R1.3, R4.5, R4.7
- **Depends on:** 002 T1
- **Do:** `V4__item_resources.sql`; `ResourceType`, `ResourceHost`, `LinkOrigin`, `Candidate`, `ResourceStatus`; `ItemResourceRepository`, `LinkCheckCacheRepository`.
- **Tests:** `ItemResourceRepositoryIT` (insert once per item and URL, due query, status updates, cascade delete with item); `LinkCheckCacheRepositoryIT`.
- **Done when:** migration applies on top of V3.
- **Status:** done

## T2 — Link normaliser and relevance guard
- **Covers:** R2.1–R2.4, R3.1, R3.2
- **Depends on:** T1
- **Do:** Pure `ResourceLinkNormaliser` and `ResourceRelevance` per the design tables.
- **Tests:** `ResourceLinkNormaliserTest` (every table row and edge case); `ResourceRelevanceTest`.
- **Done when:** all rows pass; no Spring dependency.
- **Status:** done

## T3 — Collect links at ingestion
- **Covers:** R1.1–R1.3
- **Depends on:** T2, 001 T8
- **Do:** `FoundLink` and `RawEntry.links`; `FeedParser` extracts anchors and plain URLs, discarding text; `SourceIngestor` stores candidates for new items.
- **Tests:** `FeedParserTest` with a fixture whose description and `content:encoded` contain links; `IngestionJobIT` asserts candidates stored as `PENDING` and that no content text exists in any table.
- **Done when:** fixture run stores exactly the expected candidates.
- **Status:** done

## T4 — Host API checks
- **Covers:** R4.1–R4.3, R4.6, R4.7
- **Depends on:** T1
- **Do:** `HostApis` for GitHub, GitLab, Hugging Face, arXiv with per-host limiters, base URLs from config, optional GitHub token, shared User-Agent.
- **Tests:** `HostApisIT` with WireMock: found, missing, GitHub rename, 403/429, timeout, token header present only when configured, arXiv spacing.
- **Done when:** all pass with no network access.
- **Status:** done

## T5 — Check job, cache and retries
- **Covers:** R4.4, R4.5, non-functional
- **Depends on:** T3, T4
- **Do:** `ResourceCheckJob` with ShedLock, cache reuse, retry delays, give-up after 3, skip items older than 72 hours; add trigger to `SchedulingConfig`.
- **Tests:** `ResourceCheckJobIT` (fixed clock: cache shared across two items, retry timing, give-up, limiter stops the batch).
- **Done when:** all pass.
- **Status:** done

## T6 — Resources in the API
- **Covers:** R5.1, R5.2
- **Depends on:** T5, 004 T1
- **Do:** Query verified resources per story (single extra query per page, no N+1), order and limit, labels; update `openapi.json`; regenerate the client.
- **Tests:** `CatalogControllerIT` (only verified, order, max 3, labels and names, empty list); `OpenApiContractIT`.
- **Done when:** contract updated and tests pass.
- **Status:** done

## T7 — Open-source row on cards
- **Covers:** R6.1–R6.4
- **Depends on:** T6, 004 T4
- **Do:** `ResourceLinks` component; add to `StoryCard` and `FeedCard`; e2e mock data with resources.
- **Tests:** `ResourceLinks.test.tsx` (labels, monospace name, link attributes and accessible names, hidden when empty); card tests; Playwright and axe on a story with links at 360 px and 1280 px.
- **Done when:** all pass, no new axe violations.
