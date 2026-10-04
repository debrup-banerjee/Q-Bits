# 006 — Daily digest: Tasks

Each task is 2–4 hours, leaves the full check green, and can be tested on its own.

## T1 — Editions schema and schedule
- **Covers:** R1.1, R1.4, R4.1
- **Depends on:** 005 T1
- **Do:** `V5__editions.sql`; `EditionRepository`; pure `EditionSchedule` (cut-off in `Asia/Kolkata`, next cut-off, window starts); `qbits.digest.*` config record.
- **Tests:** `EditionScheduleTest` (before/after 06:00 IST, across midnight UTC); `EditionRepositoryIT`.
- **Done when:** migration applies on top of V4.

## T2 — Batch writer
- **Covers:** R2.1, R2.3, R2.6
- **Depends on:** T1
- **Do:** `BatchStoryWriter` interface; `AnthropicBatchStoryWriter` using the SDK's batches API, sharing the request builder with `AnthropicStoryWriter`; `FakeBatchStoryWriter`.
- **Tests:** `AnthropicBatchStoryWriterTest` with WireMock (create body, status, results incl. errored/expired).
- **Done when:** tests pass with no real key.

## T3 — Shared draft handling
- **Covers:** R2.4
- **Depends on:** T1
- **Do:** Extract `applyDraft` from `StoryService` so realtime and batch paths share validation and saving; add `WRITTEN` status for held-back stories.
- **Tests:** existing `StoryServiceIT` still passes; new cases for `WRITTEN`.
- **Done when:** realtime behaviour unchanged in tests.

## T4 — Edition job and publisher
- **Covers:** R1.2, R1.3, R2.2, R2.4, R2.5, R3.1–R3.3, R6.1, R6.2, R6.4
- **Depends on:** T2, T3
- **Do:** `EditionJob` tick (cut, submit, poll, process, retry round, publish, deadline); `EditionPublisher`; `mode` switch so `StoryJob` runs only in realtime mode; scheduler wiring.
- **Tests:** `EditionJobIT` per the design's test list.
- **Done when:** a scripted day goes from cut-off to published edition in tests.

## T5 — Windows and edition API
- **Covers:** R4.1–R4.3, R5.1, R5.2, R6.5
- **Depends on:** T4
- **Do:** Anchor catalog windows to the latest cut-off; `/api/v1/edition`; `X-Data-As-Of` from edition; health detail; update OpenAPI contract and regenerate the client.
- **Tests:** `CatalogControllerIT` additions; `EditionControllerIT`; `OpenApiContractIT`.
- **Done when:** stories stay visible all day until the next edition replaces them.

## T6 — Operator command
- **Covers:** R6.3
- **Depends on:** T4
- **Do:** `--run-edition-now` under the `admin` profile.
- **Tests:** `EditionAdminIT`.
- **Done when:** an edition can be cut on demand.

## T7 — Web: edition line, late notice, remove live updates
- **Covers:** R5.3–R5.5
- **Depends on:** T5
- **Do:** `useEdition` hook; LatestPage edition and next-edition lines; late notice; remove `NewStoriesButton`, `useNewStories`, `countNewer` and their tests; update About page wording; update Playwright tests.
- **Tests:** `LatestPage.test.tsx`, client hook test, Playwright and axe.
- **Done when:** all checks pass and no polling remains on AI Latest.
