# 001 — Feed ingestion: Review

- **Date:** 2026-10-04
- **Scope:** all tasks T1–T9 (commits `f5422b5` … `d677105`), reviewed against the code as it stands on `main` at `a0ce8ff`. Changes made later by approved specs 002–006 are treated as superseding, not as drift.
- **Gate:** `.status` reads `tasks:approved` and has done so since the first commit (`f5422b5`). Note: that value was committed in the same commit as the T1 code, so the history holds no separate approval record for 001 (specs 004–006 each have one). Not blocking; worth confirming by the owner.

## Verdict

**CHANGES REQUESTED**

Three blocking findings (two principle violations, one convention) and one criterion that fails. The core pipeline is sound and well tested: 36 of 38 rows pass.

| PASS | FAIL | NO TEST |
|---|---|---|
| 36 | 1 | 1 |

## Full check (recorded today; not re-run during this review)

| Check | Result |
|---|---|
| Backend `mvn verify` | BUILD SUCCESS: 116 unit tests, 115 integration tests, Spotless clean |
| Web `npm run verify` | passed: api-client 6 tests, web 54 tests, typecheck, lint, Prettier |
| Playwright end-to-end | 44 passed, 2 skipped (phone and desktop, axe checks included) |

Spec 001 touches only `backend/` and `config/`, so the backend check is the one that applies.

## Acceptance criteria

All paths are under `backend/src/` unless stated. Test classes are under `test/java/com/qbits/`.

| Id | Status | Code | Test |
|---|---|---|---|
| R1.1 | PASS | `main/java/com/qbits/sources/domain/SourcesFileParser.java:60-104`, `sources/SourcesConfig.java:15` | `SourcesFileParserTest.loadsAllFieldsOfAValidFile`, `SourceRegistryTest.exposesOnlyEnabledSources` |
| R1.2 | PASS | `SourcesFileParser.java:79-85`, `:150-165` | `SourcesFileParserTest.rejectsMissingRequiredFieldNamingEntryAndField`, `rejectsDuplicateIds`, `rejectsInvalidUrls`, `reportsEveryProblemAtOnce`; `SourceRegistryTest.refusesToStartOnInvalidFile` |
| R1.3 | **FAIL** | No dedicated code. `ingestion/HttpConfig.java:20` follows redirects to any host, so a feed fetch can reach a URL that is neither a registered `feedUrl` nor its host's `/robots.txt`. No task covers R1.3. | none |
| R1.4 | PASS | `SourcesFileParser.java:36-43` | `SourcesFileParserTest.rejectsEnabledSourceWithoutTermsReview`, `disabledSourceMayLackTermsReview` |
| R2.1 | PASS | `ingestion/SourceIngestor.java:231-238`, `SourcesFileParser.java:217-232` | `IngestionJobIT.doesNotFetchBeforeIntervalHasPassed`, `rerunningSameContentCreatesNothing`; `SourcesFileParserTest.rejectsIntervalBelowFifteenMinutes` |
| R2.2 | PASS | `ingestion/FeedFetcher.java:55-60` | `FeedFetcherIT.sendsConditionalHeadersAndHandlesNotModified`, `returnsBodyAndValidators`; `IngestionJobIT.usesStoredValidatorsAndRecordsNotModified` |
| R2.3 | PASS | `FeedFetcher.java:65`, `SourceIngestor.java:123-124` | `IngestionJobIT.usesStoredValidatorsAndRecordsNotModified` |
| R2.4 | PASS | `ingestion/HttpConfig.java:27`, `main/resources/application.yml:43` | `FeedFetcherIT.sendsHonestUserAgent` (see minor M5 on the version) |
| R2.5 | PASS | `ingestion/IngestionJob.java:47-51`, due check `SourceIngestor.java:236-238` | `IngestionJobIT.twoInstancesFetchEachSourceOnce` |
| R3.1 | PASS | `ingestion/RobotsPolicy.java:48-66` (DB cache, 24 h TTL) | `RobotsPolicyIT.allowsWhenRobotsPermits`, `usesCacheFor24Hours` |
| R3.2 | PASS | `RobotsPolicy.java:104-106`, `SourceIngestor.java:96-98` | `RobotsPolicyIT.disallowsWhenFeedPathIsBlocked`, `honoursRulesForOurAgentName`; `IngestionJobIT.skipsSourceBlockedByRobots` |
| R3.3 | PASS | `RobotsPolicy.java:45`, `:107-110`; `SourceIngestor.java:105-109` | `RobotsPolicyIT.returnsCrawlDelay`; `IngestionJobIT.usesCrawlDelayWhenLongerThanInterval` |
| R3.4 | PASS | `RobotsPolicy.java:77-80`, `:94-97` | `RobotsPolicyIT.treatsMissingRobotsAsAllowed`, `skipsWhenRobotsServerErrors`, `skipsWhenRobotsTimesOut` (see minor M1) |
| R4.1 | PASS | `ingestion/domain/Item.java`, `main/resources/db/migration/V1__ingestion.sql:21-34` | `ItemRepositoryIT.insertsAndReadsBackAllFields`, `itemsTableHasNoColumnForArticleBodies`. Open-source link URLs in `item_resources`: superseded by 005 (principles updated to match). |
| R4.2 | PASS | `ingestion/domain/ItemNormaliser.java:47`, `:59-78`; description/summary only in `ingestion/FeedParser.java:55` | `ItemNormaliserTest.stripsHtmlAndCollapsesWhitespaceInExcerpt`, `truncatesLongExcerptAtWordBoundaryWithEllipsis`, `keepsShortExcerptUnchanged`; `FeedParserTest.neverReadsFullContentElements` |
| R4.3 | PASS | `ItemNormaliser.java:84-135` | `ItemNormaliserTest.buildsCanonicalUrl` |
| R4.4 | PASS | `ItemNormaliser.java:38-42` | `ItemNormaliserTest.usesFetchTimeAndMarksEstimatedWhenNoDate`, `fallsBackToUpdatedDate` |
| R4.5 | PASS | `ItemNormaliser.java:29-36`, counted at `SourceIngestor.java:165` | `ItemNormaliserTest.dropsEntryWithoutTitle`, `dropsEntryWithoutUsableLink`; `IngestionJobIT.ingestsFixtureFeedsEndToEnd` |
| R5.1 | PASS | `main/java/com/qbits/relevance/domain/RelevancePreFilter.java:27-29` | `RelevancePreFilterTest.keepsEverythingFromAiNativeSourcesWithFullScore` |
| R5.2 | PASS | `RelevancePreFilter.java:30-37`, keywords from `application.yml:81-120` | `RelevancePreFilterTest.addsWeightsFromTitleAndExcerptUntilThreshold` |
| R5.3 | PASS | `RelevancePreFilter.java:41-45` | `RelevancePreFilterTest.matchesAiOnlyAsAWholeWord`, `matchesCaseInsensitively` |
| R5.4 | PASS | `RelevancePreFilter.java:26` (takes title and excerpt only), `SourceIngestor.java:170` | `RelevancePreFilterTest.addsWeightsFromTitleAndExcerptUntilThreshold` |
| R5.5 | PASS | `SourceIngestor.java:171-172`, `items_not_ai` column | `IngestionJobIT.ingestsFixtureFeedsEndToEnd` (asserts `itemsNotAi == 1`) |
| R6.1 | PASS | `V1__ingestion.sql:24`, `ingestion/persistence/ItemRepository.java:34` | `ItemRepositoryIT.secondInsertOfSameCanonicalUrlIsIgnored` |
| R6.2 | PASS | `ingestion/Deduplicator.java:21-24`, `ItemRepository.java:60-71`, `ItemNormaliser.java:138-144` | `IngestionJobIT.treatsSameTitleFromSameSourceAsDuplicate`, `ItemRepositoryIT.findsSameTitleFromSameSourceWithinWindow`, `ItemNormaliserTest.titleKeyIgnoresCaseAndPunctuation` |
| R6.3 | PASS | `Deduplicator.java`, `ItemRepository.java:34` | `IngestionJobIT.rerunningSameContentCreatesNothing` |
| R7.1 | PASS | `FeedFetcher.java:31-44`, timeouts `HttpConfig.java:19`, `:24` | `FeedFetcherIT.retriesAfterTimeoutThenSucceeds`, `givesUpAfterTwoRetriesOnServerErrors`, `doesNotRetryClientErrors` (see blocking B2: also retries 429) |
| R7.2 | PASS | `SourceIngestor.java:196-229`, `ingestion/persistence/FetchLogRepository.java:22-45` | `IngestionJobIT.ingestsFixtureFeedsEndToEnd`; `StateAndLogRepositoryIT.recordsFetchLogNewestFirst` |
| R7.3 | PASS | `IngestionJob.java:63-80`, `SourceIngestor.java:113-116` | `IngestionJobIT.failingSourceDoesNotAffectOthers` |
| R7.4 | PASS | `ingestion/SourceHealthTracker.java:26-50` | `SourceHealthTrackerTest.becomesDegradedAfterFiveFailuresInARow`, `oneSuccessClearsDegraded` (the WARN line itself is not asserted) |
| R7.5 | PASS | `ingestion/SourcesHealthIndicator.java:30-51` | `SourcesHealthEndpointIT.healthShowsPerSourceDetail` |
| R8.1 | PASS | `IngestionJob.java:60-61`, `ingestion/SourceVisibility.java`, `ItemRepository.java:212-221` | `SourceAdminIT.disablingASourceHidesItsItemsAndReEnablingShowsThem`, `itemsOfSourcesRemovedFromTheFileAreHidden` (see minor M7) |
| R8.2 | PASS | `main/java/com/qbits/sources/SourceAdminCommands.java:35-51` | `SourceAdminIT.purgeDeletesEveryItemOfTheSource` |
| R9.1 | PASS | `ItemNormaliser.java:43-45` | `ItemNormaliserTest.dropsEntriesOlderThan72Hours` |
| R9.2 | PASS | `ingestion/RetentionJob.java:32-43`, `SchedulingConfig.java` daily cron; summaries removed by `on delete cascade` (`V2__stories.sql:4`, from 002) | `RetentionJobIT.deletesItemsPublishedMoreThanSevenDaysAgo`; `StoryRepositoryIT.deletingAnItemDeletesItsStory` |
| NF: 50 sources under 2 minutes | **NO TEST** | virtual-thread fan-out, `IngestionJob.java:63` | none |
| NF: tests never touch the network | PASS | WireMock fixtures, `test/resources/feeds/` | all `*IT` feed and robots tests |
| NF: no column for article bodies | PASS | `V1__ingestion.sql:26` check constraint | `ItemRepositoryIT.databaseRejectsExcerptLongerThan300Characters`, `itemsTableHasNoColumnForArticleBodies` |

## Principle violations (blocking)

**B1. Redirects are followed to any host without a robots.txt check.**
`main/java/com/qbits/ingestion/HttpConfig.java:20` sets `followRedirects(HttpClient.Redirect.NORMAL)`. If a feed URL answers with a 301/302 to another host, the fetcher requests that host without checking its robots.txt, and the host is not in `config/sources.yml`. This breaks "Check robots.txt before fetching", "Fetch only sources listed in `config/sources.yml`" and R1.3. The same `RestClient` bean is reused by the link checks (`resources/HostApis.java:32`, spec 005), so the fix applies there too.
Expected: do not follow redirects, or follow them only on the same host. Record a cross-host redirect as `FAILED` with the target in `error`, so the operator can update `feedUrl`. Add a WireMock test for both cases, tagged `// 001 R1.3`.

**B2. HTTP 429 is retried at once, ignoring the publisher's rate limit.**
`main/java/com/qbits/ingestion/FeedFetcher.java:95` treats 429 as retryable, so a rate-limited feed is requested two more times within about 6 seconds and `Retry-After` is not read. This conflicts with "Never bypass … rate limits", and R7.1 allows retries only for timeouts and 5xx. No test covers it.
Expected: do not retry on 429. Record `FAILED`, and if possible honour `Retry-After` by pushing the source's next due time back.

**B3. R1.3 has no task and no test.**
Principles (Process): "Every acceptance criterion has a test. A criterion with no test is not done." No task in `tasks.md` lists R1.3, and no test references it. Fixing B1 with a test tagged `// 001 R1.3` closes this.

## Convention issues

### Blocking

**C1. A feature reaches into another feature's persistence.**
`main/java/com/qbits/sources/SourceAdminCommands.java:3` imports and calls `com.qbits.ingestion.persistence.ItemRepository` directly. The conventions say features talk only through each other's `service` classes. Route purge through an ingestion service (for example `SourceVisibility` or a new `ItemAdminService`).

### Minor

- **M1.** `RobotsPolicy.java:77-97`: every non-5xx, non-200 robots.txt answer (401, 403, 429) is treated as "no robots.txt" and cached as allowed for 24 h. R3.4 covers only 404 and 5xx. A 429 on robots.txt in particular should be treated as unavailable (skip this cycle), not as permission.
- **M2.** `FeedFetcher.java:71`, `:81`, `:95`: a feed over the 5 MB cap throws inside the exchange, is reported as `network: IOException` with no HTTP status, and so is retried. An oversized feed is downloaded three times. Return a non-retryable failure instead.
- **M3.** `SourceIngestor.java:114`: logs `e.toString()` of any runtime exception at ERROR, and stores it in `source_fetch_log.error`. A database constraint error from Postgres includes "Failing row contains (…)", which would put the title and excerpt in logs. Current truncation makes this unlikely, but the logging convention says never log excerpts. Log the exception class and a short message only.
- **M4.** `IngestionJob.java:49`, `RetentionJob.java:34`: `Instant.now()` instead of the injected `Clock`. This has no effect in practice because ShedLock uses database time, but it breaks the Java patterns convention.
- **M5.** `main/resources/application.yml:43`: the User-Agent version is hard-coded `0.1`. The design says `QBits/${app.version}` and `pom.xml` is `0.1.0-SNAPSHOT`, so the two will drift apart on the first release.
- **M6.** `SourcesFileParser.java:30`: the 15-minute minimum is a constant here and also `qbits.ingestion.min-interval` in config. Keep one source of truth.
- **M7.** `SourcesConfig.java:15` loads the sources file once at startup. Disabling a source therefore needs a restart, and items are hidden only on the first ingestion run after it (`IngestionJob.java:60`, 30 s initial delay, and only on the instance that holds the lock). During a rolling restart, an old instance still running the previous file can un-hide them again. R8.1 says "from the next cycle", so this passes, but the principle "hides its stories at once" is only met after a restart. Document this in the operator notes, or run the visibility sync at startup.
- **M8.** Test naming: T9 lists `SourceHealthIT`, but the tests are `SourceHealthTrackerTest` (unit) and `SourcesHealthEndpointIT`. They cover the same ground.
- **M9.** `SourcesFileParser.parseStructureOnly` (`:51`) exists only for the test `projectSourcesFileIsStructurallyValid`. Production code added for one test.

## Drift from design

| Design | Implementation | Note |
|---|---|---|
| `SourceHealth` component | `SourceHealthTracker` + `SourcesHealthIndicator` (`SourceHealth` is a domain enum) | Same behaviour, split differently. Minor. |
| `SourceAdminCommands` handles disable and purge as a `CommandLineRunner` | Purge in `SourceAdminCommands` (`ApplicationRunner`); disable handling in `ingestion/SourceVisibility` | Minor; see C1. |
| Keyword map with 16 terms (e.g. Gemini 0.8, NVIDIA 0.6) | List of 37 `{term, weight}` entries, Gemini 0.6, NVIDIA 0.5, plus new terms (A.I., Claude, Copilot, Mistral, chip, robotics, …) | The spec leaves weights as an open question for tuning. Record the tuned list in `design.md` so spec and code agree. |
| `user-agent: QBits/${app.version} …` | Literal `QBits/0.1 …` | See M5. |
| Health detail `{ status, lastSuccessAt, consecutiveFailures }` | Same, plus status `NOT_YET_FETCHED` and top-level `enabled` / `degraded` counts | Additive. Minor. |
| Schema in design | Adds `check` constraints on `health`, `status` and `error`, plus indexes `items_published_idx` and `source_fetch_log_source_idx` | Stricter, harmless. Minor. |

Superseded by later approved specs (not drift):
- `FeedParser` reads link targets from content elements and `RawEntry.links`; `SourceIngestor.java:192` calls `ResourceCollector`: superseded by 005 (URLs only; tested by `FeedParserTest.collectsLinksFromEntryDescriptionAndContentWithoutText` and `IngestionJobIT.storesOpenSourceCandidatesAndNeverTheContentText`).
- `items.story_note`: superseded by 002. `items.edition_id`, `items.retry_feedback` and the `PENDING`-to-edition hand-off: superseded by 006.
- Shared `feedRestClient` used for host API checks: superseded by 005 (see B1).
- `SchedulingConfig` runs the digest, link-check and story jobs alongside ingestion: superseded by 002, 005 and 006.

## Unrequested work (scope)

- 429 retry (B2): not asked for, and conflicts with a principle.
- 5 MB feed body cap (`FeedFetcher.java:21`): sensible protection, but not in any task. Keep it, fix M2, and add it to the design.
- `retry-backoff`, `robots-agent-name` and `degraded-after-failures` config keys: small and reasonable. Add them to the design's config contract.
- Source `id` format check and `sectionHint` slug check (`SourcesFileParser.java`): reasonable edge validation. Add them to R1.2 or the design.

## Notes outside 001's code

- `config/sources.yml` (commit `a0ce8ff`): all 24 sources are enabled. The file header says nine terms links are "still to be opened once" and that no `feedUrl` has yet been confirmed as a working feed. The principles require each enabled entry to have reviewed terms. The owner has attested to the review date, so this is not blocking for 001, but those nine terms pages should be opened before the next release. B1 also matters more here: an unverified feed URL that redirects is exactly where a cross-host fetch would happen.
- No summaries are produced by this spec, so the editorial-style check does not apply. The 360 px / 1280 px layout check does not apply either (no front-end change).

## Resolution (2026-10-04)
All blocking items above were fixed with tests, in two rounds (`fix(…)`, `test(…)`, `refactor(…)` commits on 2026-10-04), along with most minor items. Deb approved the design changes in the session, and they are written up under "Approved changes (2026-10-04)" in this spec's design.md and requirements.md. Full check after the fixes:
- Backend `mvn verify`: BUILD SUCCESS, 151 unit tests and 162 integration tests.
- `npm run verify`: passed, including the client drift check (api-client 6 tests, web 65 tests).
- Browser tests: 65 passed, 7 skipped (each skip is a test meant for the other screen size).
