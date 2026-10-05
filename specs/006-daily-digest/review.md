# 006 — Daily digest: Review

**Reviewed:** 2026-10-04, commits `24fd95d`..`f2f1a3d` (006/T1–T7, plus fix `9cb9f55`).
**Verdict: CHANGES REQUESTED**

Six blocking defects. Most are in the edition job's failure paths: a failed results download, a late first run, a failed retry submit and the call cap on the retry batch. Two criteria also have no test that proves them. The normal path (cut-off, then batch, then poll, then one retry round, then all-at-once publish) is sound and well tested. The windows, the edition API and the web changes match the spec.

## Gate
`.status` read `tasks:approved` at `33e391b` ("approve spec 006 (approved by Deb)"). That commit came before the first 006 code commit `24fd95d`, and the file has not changed since. **PASS.**

## Full check (recorded results, today's run)
| Part | Result |
|---|---|
| Backend `mvn -f backend/pom.xml verify` | BUILD SUCCESS: 116 unit tests, 115 integration tests, spotless clean |
| Web/client `npm run verify` | Passed: api-client 6 tests, web 54 tests, typecheck, lint, prettier |
| Playwright (phone + desktop, axe) | 44 passed, 2 skipped |

The API contract changed, and both parts were run. `packages/api-client/openapi.json` and `schema.ts` were regenerated in `f278883`.

## Criteria

**Totals: 20 PASS · 4 FAIL · 2 NO TEST** (26 criteria)

| Id | Status | Code | Test |
|---|---|---|---|
| R1.1 | PASS | `EditionSchedule.java:30` latestCutoffAtOrBefore; `EditionJob.java:105-112` | `EditionScheduleTest.latestCutoffBeforeAndAfterSixAmIst`, `EditionJobIT.onlyOneEditionPerDay`, `cutsAtSixAmIst…` |
| R1.2 | **FAIL** | `ItemRepository.java:132-147` assignToEdition; `EditionJob.java:105-118` | `EditionJobIT.cutsAtSixAmIstWithItemsFetchedBeforeTheCutoffNewestFirst`. Normal path only. An edition first created more than 6 h after its cut-off includes no items. See B2. |
| R1.3 | PASS | `ItemRepository.java:138` (`fetched_at <= :cutoff`) | `EditionJobIT.cutsAtSixAmIst…` (the `late` item) |
| R1.4 | PASS | `V5__editions.sql`, `EditionRepository.java` | `EditionRepositoryIT.tracksAnEditionFromCollectingToPublished` (FAILED status not exercised) |
| R2.1 | PASS | `AnthropicBatchStoryWriter.java:33-54`, shared `StoryRequests` (used by `AnthropicStoryWriter.java:40-63`) | `AnthropicBatchStoryWriterTest.submitsOneRequestPerItemWithTheSameParamsAsSingleCalls` |
| R2.2 | **FAIL** | `EditionJob.java:168-187` (main batch respects room); `EditionJob.java:209` (retry batch ignores the cap) | `EditionJobIT.respectsTheDailyCapAndLeavesTheRestForTomorrow` (main batch only). See B3. |
| R2.3 | PASS | `EditionJob.java:220-232`, `EditionRepository.markSubmitted/markRetrying/markPolled` | `EditionJobIT.publishesTheWholeEditionAtOnceWhenTheBatchEnds`, `AnthropicBatchStoryWriterTest.reportsStatus` |
| R2.4 | **FAIL** | `EditionJob.java:235-279`; `AnthropicBatchStoryWriter.java:77-91` | `EditionJobIT.notAiAndErroredResultsAreRecorded`, `StoryServiceIT.holdsBackAValidBatchDraftAsWritten`. A failed results download is treated as "no results". The stored reason drops the error. See B1 and B5. |
| R2.5 | PASS | `EditionJob.java:131-141, 199-212`; `StoryService.applyDraft` | `EditionJobIT.runsOneRetryRoundWithFeedback`, `stillInvalidAfterRetryIsRejected`, `StoryServiceIT.firstInvalidBatchDraftAsksForRetry…` |
| R2.6 | PASS | `StoryRequests.userText`, `StoryInputs.of` | `AnthropicBatchStoryWriterTest.submitsOneRequest…` (exact property names) |
| R3.1 | PASS | `EditionPublisher.java:28-45` (one `TransactionTemplate`) | `EditionJobIT.publishesTheWholeEditionAtOnceWhenTheBatchEnds` |
| R3.2 | PASS | `StoryService.applyDraft` saves `WRITTEN`; `StoryQueryRepository.java:43` shows `PUBLISHED` only | `StoryServiceIT.holdsBackAValidBatchDraftAsWritten`, `EditionJobIT.publishesTheWholeEdition…` |
| R3.3 | PASS | `EditionJob.java:115-119`, `EditionPublisher` releaseUnfinished | `EditionJobIT.publishesWhatIsReadyAfterSixHours`. The test has no WRITTEN item and does not check the warning (see minor M1). |
| R4.1 | PASS | `EditionQueries.windowEnd` (line 32), `EditionRepository.findLatestPublished` | `CatalogControllerIT.windowsAreAnchoredToTheLatestEditionCutoff` |
| R4.2 | PASS | `CatalogService.list/windowStart` | `CatalogControllerIT.windowsAreAnchoredToTheLatestEditionCutoff`, `EditionScheduleTest.windowsEndAtTheCutoff` |
| R4.3 | PASS | `EditionQueries.java:33` (`orElseGet(clock::instant)`) | Covered only by the existing `CatalogControllerIT.hoursLimitsTheWindow` (004), which runs with no edition. There is no test tagged 006 R4.3 (minor M10). |
| R5.1 | PASS | `EditionController.java` | `CatalogControllerIT.editionEndpointAndDataAsOf` (200 + 404 `NO_EDITION`). The extra `late` field is drift (D2). |
| R5.2 | PASS | `EditionController` header, `CatalogService.dataAsOf` | `CatalogControllerIT.editionEndpointAndDataAsOf` |
| R5.3 | PASS | `LatestPage.tsx:35-50`, `editionText.ts` | `LatestPage.test.tsx` "shows which edition it is…", `editionText.test.ts` (today/yesterday/older), Playwright `latest.spec.ts` |
| R5.4 | **NO TEST** | Button, hook and `countNewer` removed; refresh relies on the `refetchOnWindowFocus` default (`web/src/app/providers.tsx:13`) with `staleTime` 60 s | The removal is tested (`LatestPage.test.tsx`, Playwright: no "new stories" button). No test shows that the feed or edition reloads when the tab is reopened after the cut-off. |
| R5.5 | PASS | `EditionQueries.isLate`, `LatestPage.tsx:53-60` | `EditionQueriesTest.lateOnlyAfterTwoHoursWithoutTodaysEdition`, `LatestPage.test.tsx` "running late", Playwright |
| R6.1 | **FAIL** | `EditionJob.java:189-191` (main batch: OK); `EditionJob.java:213-217` (retry batch: publishes at once) | `EditionJobIT.retriesSubmittingEveryThirtyMinutes` (main batch only). See B4. |
| R6.2 | PASS | `EditionJob.java:273-274`, `AnthropicBatchStoryWriter.java:90` | `EditionJobIT.expiredRequestsGoBackForTheNextEdition`, `AnthropicBatchStoryWriterTest.readsSucceededErroredAndExpiredResults` |
| R6.3 | PASS (functional) | `EditionAdminCommands.java`, `EditionJob.cutNow` | `EditionAdminIT.cutsAndSubmitsAnEditionOnDemand`. The concurrency defect is B6. |
| R6.4 | PASS | `SchedulingConfig.java:55-81` | `SchedulingModeTest` (both modes). Realtime-mode windows: see M6. |
| R6.5 | **NO TEST** | `EditionHealthIndicator.java:20` | `SourcesHealthEndpointIT` asserts only that a `"digest"` key exists. Cut-off, published time and status are never asserted, and the test would pass with an empty indicator. |

## Blocking

**B1. A failed results download publishes the edition and throws away the batch.** `EditionJob.java:237-242` turns `WriterUnavailable` or `WriterRejected` from `batches.results()` into `List.of()`. The callers treat that as "no retries needed" and publish at once (`EditionJob.java:128-130`, and `138-139` for the retry round). A single network error while streaming results publishes an empty edition. It also releases every item to tomorrow, so stories already written and paid for are written and paid for again. The correct behaviour is to leave the edition `SUBMITTED`/`RETRYING` and try the download again at the next poll, up to the deadline (R2.4, R3.1). No test covers this.

**B2. An edition first cut more than 6 h after its cut-off is published empty and never submitted.** `EditionJob.java:105-118`: when there is no open edition, `tick()` creates one for `latestCutoffAtOrBefore(now)`. The deadline check then fires straight away and publishes it with 0 stories. This happens on the first production deploy after 12:00 IST, or after any outage that spans cut-off + 6 h. The result:
- None of the waiting items are included (R1.2).
- AI Latest is empty for the day.
- The empty edition counts as "today's", so the R5.5 late notice is suppressed.

The deadline should apply only to an edition that was actually submitted, or a missed day should be cut at "now".

**B3. The retry batch ignores the daily call cap.** At `EditionJob.java:209`, `budget.takeUpTo(..., Integer.MAX_VALUE)` is called without checking the remaining room. If the main batch used the full cap of 500, the retry round still sends every invalid item, so daily calls can go above `daily-call-cap`. In 002, each retry attempt goes through the gate (`StoryService.process`). R2.2 is not met.

**B4. A failed retry-batch submit publishes at once instead of retrying every 30 minutes.** `EditionJob.java:213-217` catches `WriterUnavailable` (service down, rate limit) on the retry submit and calls `publisher.publish` straight away. R6.1 covers any batch submit and requires a retry every 30 minutes up to the deadline. The main-batch path does this correctly (`markSubmitFailed`), but the retry path does not, and nothing tests it.

**B5. Errored results do not keep the error.** `AnthropicBatchStoryWriter.java:88` hard-codes the reason `"PROVIDER_ERROR"`, and `EditionJob.java:269-270` prefixes it again. The stored note is therefore always `PROVIDER_ERROR: PROVIDER_ERROR`, and the error type and message from the result (for example `invalid_request_error` or `overloaded_error`) are lost. R2.4 says "request error → `REJECTED` with the error". `notAiAndErroredResultsAreRecorded` does not assert the stored reason.

**B6. The operator cut can double-assign items: not safe to run twice (principle: Architecture).**
- `EditionJob.cutNow` (`EditionJob.java:91-99`) runs outside the `digest` ShedLock. Its own Javadoc says the admin instance keeps running next to the service, so `cutNow` can run at the same moment as another instance's `tick()`.
- Both then call `assignToEdition` (`ItemRepository.java:135-141`). This is `update … where id in (select … edition_id is null … limit)`. Under READ COMMITTED, the blocked statement re-checks only the outer `WHERE` (`id in (…)`), not the subquery's `edition_id is null`. Items already submitted in the first edition's batch can therefore be moved to the second edition.
- The effect: those items are sent twice, and the first edition ignores their results.

Fix: take the same lock in `cutNow`. Also add `and edition_id is null` to the outer `WHERE`, or use `for update skip locked` in the subquery.

**B7. Two criteria have no proving test (principle: Process, "Every acceptance criterion has a test").**
- R6.5: `SourcesHealthEndpointIT.java:52` checks only the `"digest"` key.
- R5.4: the reload when the tab is reopened has no test.

## Minor

- **M1. Deadline handling.**
  - `EditionJob.java:115-119` checks the deadline before polling. A batch that ended inside the last poll interval before the deadline is published without its results.
  - The in-flight batch is not cancelled, so the same items are paid for again in the next edition.
  - `publishesWhatIsReadyAfterSixHours` has no `WRITTEN` item and does not check the warning.
- **M2. Submit is not atomic, and some SDK errors are unmapped.**
  - In `EditionJob.java:185-187`, submit, then budget, then `markSubmitted` are not atomic. A database error after `submit` leaves a batch nobody tracks, and the next tick submits again.
  - `AnthropicBatchStoryWriter.call` (`:93-104`) does not map `AnthropicInvalidDataException` or other non-service `AnthropicException`s. Neither are failures inside `draftFrom` other than `WriterRejected` (for example `_input().convert` on a non-object).
  - On submit, such an error skips the 30-minute backoff and resubmits every 5 minutes.
  - On results, one malformed line blocks the whole edition until the deadline.
- **M3. Counts can be undercounted.** `editions.addCounts` runs only after the whole loop (`EditionJob.java:277`). If processing fails partway and is re-run, the items already handled are no longer `PENDING`, so they are never counted.
- **M4. Transient errors are rejected for good.** Errored results with types such as `overloaded_error` or `api_error` become permanently `REJECTED`. This matches the wording of R2.4, but the owner may prefer to send them back to `PENDING`. This is a spec question, not a code fix.
- **M5. Health detail is thin.** `EditionHealthIndicator` always reports UP. A `FAILED` latest edition does not appear, because only the latest published and open editions are read. R6.5 asks for the latest edition's status.
- **M6. Realtime mode leaves the windows anchored to the last edition.**
  - In `realtime` mode, `EditionQueries.windowEnd` stays fixed at the last edition's cut-off.
  - `StoryQueryRepository.java:43` has only a lower bound, so `hours=24` keeps growing past 24 hours.
  - `X-Data-As-Of` stays stale, and `/edition` reports `late`.
  - R6.4 promises 002 behaviour, so consider falling back to "now" in realtime mode.
- **M7. Window and cap edge cases.**
  - `assignToEdition` takes any `PENDING` item from the last 72 h. R1.2 says the last 24 h plus leftovers from earlier editions. This is harmless in practice but differs from the wording.
  - With `room = 0`, an empty edition is published (`EditionJob.java:176-178`).
- **M8. Phantom empty edition after an operator cut.** If `--run-edition-now` runs after today's cut-off has passed with no scheduled edition yet, the next tick still creates and publishes an empty edition for 06:00.
- **M9. Conventions.**
  - `BatchRequest` has an `Optional` record component (`BatchStoryWriter.java:16`). The convention allows `Optional` only as a return type.
  - `qbits.story-writer.mode` is read with `@Value` (`SchedulingConfig.java:55`) instead of the config record, and it is not validated: any typo silently means `digest`.
  - `runOnce` uses `Instant.now()` instead of the injected `Clock` (`EditionJob.java:86`; same pattern as `StoryJob`).
  - Fully qualified names appear inline in `SchedulingConfig` and `ItemRepository`.
  - `WriterBudgetRepository.takeUpTo` makes one round trip per call and is `synchronized` per JVM only.
- **M10. Test tagging and placement.**
  - R4.3 has no test tagged `// 006 R4.3`.
  - The T5 task named `EditionControllerIT`, but those tests were placed in `CatalogControllerIT`.
- **M11. Feedback is never cleared.** `retry_feedback` stays set on items that end `WRITTEN`, `PUBLISHED` or `REJECTED`. It holds only validator rule text, so this is not a content problem, but it is untidy.

## Principles checked
- **Content and legal:**
  - Batch requests send only the allowed fields (tested).
  - No new article-derived fields are stored. `retry_feedback` holds validator messages only.
  - The schema guard test was updated deliberately (`9cb9f55`).
  - Logs carry ids, counts and HTTP codes only. No excerpts or prompts.
- **Architecture:**
  - Summaries are written once a day in one batch. OK.
  - An edition is published in one transaction. OK.
  - Windows are anchored to the edition. OK.
  - Clients hold no rules. OK: the late check is in the backend.
  - "Safe to run twice": **violated**, see B6.
- **Process:**
  - The gate was honoured.
  - "Every acceptance criterion has a test": **violated** for R5.4 and R6.5 (B7).

## Design drift
- **D1. Package layout.** Everything is in `com.qbits.stories.digest`. The design places `EditionRepository` in `stories.persistence`, `EditionSchedule` in `stories.domain`, and the job, publisher and health indicator in `stories`. This also departs from the feature/layer layout in the conventions. Separately, `EditionJob` uses `ingestion.persistence.ItemRepository` directly instead of an ingestion service. That pattern already existed in 002, but it is against the conventions.
- **D2. Extra `late` field.** `GET /api/v1/edition` returns a `late` boolean that is not in the design contract. The change is additive, and keeping the rule in the backend is the better choice, but `design.md` must be updated and re-approved.
- **D3. Schema additions.** The schema adds `last_polled_at`, `next_submit_at`, `items.retry_feedback`, `editions_status_idx` and `on delete set null` on `items.edition_id`. None are in the design's data model.
- **D4. Extra config.** `qbits.digest.tick` is not in the design's config contract.
- **D5. Signature change.** `applyDraft(item, input, draft, attempt)` gained a `holdBack` flag.

## Unrequested work
- `a0ce8ff chore(config): terms links and review date for all 24 sources` is not part of spec 006. It sets `termsUrl` and `termsReviewedOn` on all sources "on the owner's attestation", and nine of the links have not yet been opened. It is outside this review. CLAUDE.md requires the owner to confirm in the session that he asked for this work outside the spec, and the principle that terms are actually reviewed before fetching applies here.
- `tasks.md` "Status: done" lines were added by the implementer. These are bookkeeping only; nothing else in the spec changed.

## Resolution (2026-10-04)
All blocking items above were fixed with tests, in two rounds (`fix(…)`, `test(…)`, `refactor(…)` commits on 2026-10-04), along with most minor items. Deb approved the design changes in the session, and they are written up under "Approved changes (2026-10-04)" in this spec's design.md and requirements.md. Full check after the fixes:
- Backend `mvn verify`: BUILD SUCCESS, 151 unit tests and 162 integration tests.
- `npm run verify`: passed, including the client drift check (api-client 6 tests, web 65 tests).
- Browser tests: 65 passed, 7 skipped (each skip is a test meant for the other screen size).
