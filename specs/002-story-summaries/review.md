# 002 — Story summaries: Review

- **Reviewed:** 2026-10-04, against `requirements.md`, `design.md`, `tasks.md`, all of `steering/` and `CLAUDE.md`.
- **Commits in scope:** `512a13e` (T1), `af31961` (T2), `462737c` (T3), `c7a2bbd` (T4), `64642ab` + `879c8ea` (T5), `0310ca1` (T6), `370c964` (T7). Later changes to the same files by specs 003–006 are treated as superseding where an approved spec asked for them.
- **Gate:** `.status` reads `tasks:approved`. PASS. (Process note: the spec entered history already approved, inside `f5422b5` (001/T1), with no separate approval commit like the ones for 004–006. Nothing to fix in code; see Minor M14.)

## Verdict: CHANGES REQUESTED

Four blocking findings: a principle conflict over what is sent to the summary service (B1), one acceptance criterion with no implementation (R6.2, B2), one criterion half with no test (R9.2, B3), and the stories feature reading another feature's persistence directly (B4). Separately, T7 (the live sample and editorial check) has not been run, so ten judgement-based criteria have no evidence yet (O1). That is not a code defect, but the spec cannot be marked `done` until it is run.

## Full check (recorded results, 2026-10-04)

| Check | Result |
|---|---|
| Backend `mvn verify` | BUILD SUCCESS: 116 unit tests, 115 integration tests, Spotless clean |
| Web `npm run verify` | Passed: api-client 6 tests, web 54 tests, typecheck, lint, Prettier |
| Playwright e2e | 44 passed, 2 skipped (phone and desktop, axe included) |
| `StoryWriterLiveIT` (T7, opt-in `-Plive`) | **Not run.** The build environments cannot reach the summary service or the news feeds |

Spec 002 changes no web code and no API contract. The web results are recorded for completeness only.

## Acceptance criteria

Totals: **24 PASS · 1 FAIL · 10 NO TEST** (35 rows; R9.2 is split into two rows).

| Id | Status | Code | Test | Notes |
|---|---|---|---|---|
| R1.1 | PASS | `StoryJob.java:93`, `ItemRepository.findPendingNewestFirst` | `StoryJobIT.processesNewestFirstAndPassesSourceContext` | Superseded by 006 in the default `digest` mode. Still active with `qbits.story-writer.mode=realtime` (`SchedulingConfig.java:78-83`). |
| R1.2 | PASS | `StoryInput.java:9-15`, `StoryRequests.java:112-125` | `AnthropicStoryWriterTest.sendsOnlyAllowedFieldsAndForcesTheTool` | Matches the spec. See B1: the spec's field list conflicts with `principles.md`. |
| R1.3 | PASS | `ItemRepository.findPendingNewestFirst` (PENDING only), ShedLock in `StoryJob.java:72-76` | `StoryJobIT.publishedStoriesAreNotRewritten` | |
| R2.1 | PASS | `StoryService.java:120-123`; readers see only `PUBLISHED` (`StoryQueryRepository.java:43`) | `StoryServiceIT.marksNonAiItemsWithReason` | |
| R2.2 | PASS | `StoryService.java:121` (`story_note`) | `StoryServiceIT.marksNonAiItemsWithReason` | |
| R3.1 | PASS | `V2__stories.sql:5-6` check; `StoryValidator` `SHAPE` | `StoryRepositoryIT.databaseOnlyAcceptsTheFourSections`, `StoryValidatorTest.missingFieldsFailShape` | |
| R3.2 | NO TEST | `story-writer.v1.md` step 2 | — | Prompt-only rule. Waiting for T7. |
| R3.3 | NO TEST | `story-writer.v1.md` step 2 | — | Waiting for T7. |
| R3.4 | NO TEST | `story-writer.v1.md` step 2 | — | Waiting for T7. |
| R3.5 | NO TEST | `story-writer.v1.md` step 2 | — | Waiting for T7. |
| R3.6 | NO TEST | Hint sent as its own field (`StoryRequests.java:118`); the prompt calls it "only a hint" | `StoryJobIT` proves the hint is sent; nothing proves the rules override it | Waiting for T7. |
| R4.1 | PASS | `StoryValidator.java:47-53` | `StoryValidatorTest.headlineOverTwelveWordsFails` | |
| R4.2 | PASS | `StoryValidator.java:55-68` | `StoryValidatorTest.summaryLengthDependsOnExcerptLength` | |
| R4.3 | NO TEST | `story-writer.v1.md` step 4 | — | Human-judged in T7. Not run. |
| R4.4 | PASS | `StoryValidator.java:70-74`, `:159-167` | `StoryValidatorTest.summaryMustNameTheSource`, `sourceNameInBracketsMayBeShortened` | |
| R4.5 | NO TEST | `story-writer.v1.md` step 4 | — | Human-judged in T7. Not run. |
| R5.1 | PASS | `StoryValidator.java:83-99`; schema `maxItems: 5` | `StoryValidatorTest.keyTermsAreLimitedAndMustAppear` | |
| R5.2 | NO TEST | `story-writer.v1.md` step 5 | — | Human-judged in T7. Not run. |
| R5.3 | PASS | `StoryValidator.java:88`, `:100-104` | `StoryValidatorTest.keyTermsAreLimitedAndMustAppear` | |
| R5.4 | NO TEST | `story-writer.v1.md` step 5 | — | Human-judged in T7. Not run. The design's "acronym spelled-out heuristic" was not built (M9). |
| R6.1 | PASS | `StoryValidator.java:108-125` | `StoryValidatorTest.numbersMustComeFromTheSource` | Digits only; units and spelled-out numbers are not compared (M2). |
| R6.2 | **FAIL** | None. The prompt never says to keep product or model names exactly as written (`story-writer.v1.md:28-31`) | — | See B2. |
| R7.1 | PASS | `StoryValidator.java:127-141` | `StoryValidatorTest.copyingEightWordsFromTheSourceFails` | The product-name exclusion was not built, so the check is stricter than the spec (M3). |
| R7.2 | PASS | `StoryValidator.java:143-157` | `StoryValidatorTest.quotingTheSourceFails` | The regex treats apostrophes as quote marks (M1). |
| R8.1 | PASS | `StoryService.java:125` runs every rule before saving | `StoryValidatorTest` (all rules) | |
| R8.2 | PASS | `StoryService.java:93-95`, `:135-137`; `StoryRequests.java:121-123` | `StoryServiceIT.retriesOnceWithFeedbackThenPublishes`, `AnthropicStoryWriterTest.includesFeedbackOnRetry` | A reply with no tool call is rejected without the retry (M5). |
| R8.3 | PASS | `StoryService.java:131-133` | `StoryServiceIT.rejectsAfterSecondInvalidDraftAndRecordsRules` | |
| R8.4 | PASS | `StoryService.java:126-128`, `:140-158` | `StoryServiceIT.publishesAValidDraft` | |
| R9.1 | PASS | `WriterBudgetRepository.tryTake`, `StoryJob.java:90-92`, `StoryService.java:71-73` | `StoryJobIT.stopsForTheDayAtTheCallCap`, `StoryServiceIT.closedGateMakesNoCallAndLeavesPending` | Still applies under 006 (006 R2.2). |
| R9.2a (stay PENDING, back off) | PASS | `StoryService.java:77-79`, `StoryJob.java:139-149`, `JobBackoffRepository` | `StoryJobIT.backsOffWhileTheServiceIsDownThenRecovers`, `StoryServiceIT.serviceOutageLeavesItemPending`, `AnthropicStoryWriterTest.rateLimitAndServerErrorsAreTemporary`, `timeoutIsTemporary` | Superseded by 006 R6.1 in `digest` mode. |
| R9.2b (ingestion unaffected) | NO TEST | Separate scheduled trigger (`SchedulingConfig.java:85-88`) | — | T5 lists the test "writer down leaves ingestion working". It does not exist. See B3. |
| R9.3 | PASS | `StoryJob.java:80`, `ItemRepository.expirePendingBefore` | `StoryJobIT.expiresPendingItemsOlderThan72Hours` | |
| R9.4 | PASS | `StoryJob.java:99-125` | `StoryJobIT.neverRunsMoreThanTheParallelLimit` | Superseded by 006 in `digest` mode. |
| R10.1 | PASS | `V2__stories.sql:10-15`, `StoryService.java:140-153`, `StoryRequests.draftFrom` | `StoryServiceIT.publishesAValidDraft`, `AnthropicStoryWriterTest.mapsToolOutputToDraft`, `StoryRepositoryIT.savesAndLoadsKeyTermsAsJson` | |
| R10.2 | PASS | `StoryAdminCommands.java:68-99`; upsert in `StoryRepository.java:80-106` | `StoryAdminIT.rewritesAStoryWithTheCurrentPromptVersion`, `rewritesEveryPublishedStoryOfASource` | A failed rewrite hides the old story (M8). |

### Non-functional (not counted above)

| Item | Status | Evidence |
|---|---|---|
| Published within 15 minutes of ingestion | Superseded by 006 | The daily digest replaces this. In `realtime` mode the 2-minute tick and batch size of 20 still allow it, but nothing measures it. T7 was meant to record it. |
| API key only from `ANTHROPIC_API_KEY` | PASS | `AnthropicConfig.java:278`. No committed key. |
| Tests use a fake writer; live suite is opt-in | PASS | `TestBeans.java:16-26`; `pom.xml` excludes `**/*LiveIT.java` unless `-Plive` is set. |

## Principle violations (blocking)

**B1. The summary service receives more than the title and excerpt.**
`steering/principles.md:17` says "Only the source title and excerpt are sent to the summary service." Requirement R1.2, `StoryInput.java:9-15` and `StoryRequests.java:112-119` also send `sourceName`, `region`, `sectionHint` and `publishedAt`. 006 R2.6 repeats the same list for batch requests. None of these fields is article content or reader data, and each one is useful. Even so, the spec conflicts with the literal principle. The rules say this must be flagged, not worked around. **Owner decision needed:** either widen the principle (for example "the source title and excerpt, plus the source's configured name, region and section hint and the publish date") or cut the request down to title and excerpt. No code change is needed if the principle is widened.

No other principle is broken. Checked: stored fields (`stories` holds only our headline, summary, key terms, section and traceability columns; `story_note` holds our reason or rule ids); no article fetching; prompts, excerpts and responses are not logged at INFO; the service is called only through `StoryWriter`; the key comes from the environment; validation runs before anything is visible; ShedLock and the database-held back-off keep instances stateless; `StoryValidator` is pure.

## Blocking findings

**B2. R6.2 is not implemented.** The design leaves R6.2 ("product and model names written as in the input") to the prompt. Neither the design's draft prompt nor `story-writer.v1.md:28-31` says it. Step 5 covers terms, acronyms, numbers and units, but not names. Fix: add the rule in a new `story-writer.v2.md` (T7 already allows a new version file), switch `prompt-version`, and include named products in the T7 sample check.

**B3. R9.2 "ingestion SHALL be unaffected" has no test.** T5 lists "writer down leaves ingestion working" under `StoryJobIT`. That test does not exist, and nothing else in the suite runs ingestion while the writer is failing. `StoryJobIT.java:125` covers only the PENDING and back-off half. `principles.md` says "Every acceptance criterion has a test." Fix: add a `StoryJobIT` (or `IngestionJobIT`) case that runs ingestion against the WireMock feeds with a writer that throws `WriterUnavailable`, and checks that new items are stored.

**B4. The stories feature reads ingestion's persistence directly.** `conventions.md` says "A feature talks to another only through that feature's `service` classes. No reaching into another feature's `persistence`." The code breaks this in `StoryService.java:4`, `StoryJob.java:4` and `StoryAdminCommands.java:5`, which all import `com.qbits.ingestion.persistence.ItemRepository`. Spec 002 added story-only queries to that repository (`findPendingNewestFirst`, `expirePendingBefore`, `updateStoryStatus`, `findIdsBySourceAndStatus`). Spec 006 has since copied the pattern (`EditionJob`, `EditionPublisher`), so the coupling is spreading. Fix: expose these operations through an ingestion service (for example `ItemStoryQueue`) and have `stories` use only that.

## Open item (not a code defect)

**O1. T7 has not been run.** `StoryWriterLiveIT` and `src/test/resources/live/sample-items.json` (20 fictional items, 5 per section) are built and excluded from the default build, as designed. The run needs a key and network access to the summary service, which the build environments do not have. Until the owner runs it with two model sizes, reviews `backend/target/live-samples.md` against `editorial-style.md` and picks a model, R3.2–R3.6, R4.3, R4.5, R5.2 and R5.4 have no evidence. The editorial sampling step of this review could not be done either: no real generated summaries exist, only test fixtures. Note: the suite records tokens per 100 stories, not cost. Cost has to be worked out from the token totals.

## Convention issues (minor)

- **M1** `StoryValidator.java:29`: the `QUOTED` pattern treats `'` and `’` as quote marks. Possessives ("OpenAI's … Google's") can wrap ordinary text, so a 4-word run shared with the excerpt (allowed by R7.1) can be falsely rejected as a quotation. Match only double quotes, or require a non-letter on the outer side of a single quote.
- **M2** `StoryValidator.java:28`, `:108-125`: R6.1 says "digits, with units". Only the digits are compared, so "3 nanometre" in the source allows "3 GB" in the summary. Spelled-out numbers ("two million") are not checked. Either check units or narrow the requirement to match the design's "number token".
- **M3** `StoryValidator.java:127-141`: the R7.1 / design exclusion for product names was not built. The Javadoc explains why. The check is stricter than the spec, which is safe, but it is still drift. Update the spec or add the exclusion.
- **M4** `StoryValidator.java:47-68` against `V2__stories.sql:7-8`: words are counted but the database limits are in characters (headline ≤ 120, summary ≤ 1000). A long-worded draft passes validation, fails on insert, throws out of `process`, stays PENDING and is sent again every tick until the cap or expiry. The error log at `StoryJob.java:131` may then include the failing row text. Add character limits as validator rules.
- **M5** `StoryRequests.java:134`: a reply with no `write_story` call raises `WriterRejected`, so the item is REJECTED with `PROVIDER_ERROR` and never retried. R8.2 treats invalid output as retry-once.
- **M6** `AnthropicStoryWriter.java:57-59`: 401, 403 and 404 map to `WriterUnavailable`. The design, T4 and the `StoryWriter.java:27` Javadoc say every 4xx except 429 is `WriterRejected`. The choice makes sense (a bad key should not reject every item), but it differs from the design. Update the design and the Javadoc.
- **M7** `StoryWriter.java:18` (also `StoryRequests.java:112`): `Optional<String>` is used as a parameter. `conventions.md` forbids that, but the approved design specifies this exact signature. Resolve it in the design (an overload or a nullable `feedback` argument), then change the code.
- **M8** `StoryAdminCommands.java:87` with `StoryService.java:121`, `:132`: when an operator rewrites a PUBLISHED story and the new draft fails validation twice or comes back as not AI, the item becomes REJECTED or NOT_AI. The previously good story disappears and its `stories` row is left behind. The spec says nothing about this. Decide whether a failed rewrite should keep the old story.
- **M9** `design.md` lists an "acronym spelled-out heuristic" for `StoryValidator`, but its rule table does not, and none was built. Remove it from the component row or add the rule.
- **M10** `StoryJob.java:152-161` and `StoryAdminCommands.java:106-115` duplicate the `StoryInput` builder that now lives in `StoryInputs` (added by 006). Use the one component, so R1.2 is enforced in one place.
- **M11** `StoryJob.java:146`: the log line is not in key=value form (`backing off for {}`). Use `backoffSeconds=…`.
- **M12** `StoryJob.java:74`: `Instant.now()` is used for the lock configuration instead of the injected `Clock`. This is harmless because ShedLock uses database time, but it breaks the "inject a Clock" rule.
- **M13** Prompt against `editorial-style.md`: `story-writer.v1.md` does not mention "one everyday comparison", "no jargon inside key-term explanations" or exact product names (B2). Consider these for v2 after T7.
- **M14** Process: spec 002 was committed already at `tasks:approved` inside a code commit (`f5422b5`, 001/T1). Later specs record approval in a separate "approved by Deb" commit. Consider doing that for every spec, so the gate can be audited.

## Design drift and unrequested work

| Item | Where | Assessment |
|---|---|---|
| `job_backoff` table and `JobBackoffRepository` | `V3__job_backoff.sql`, `stories/persistence/JobBackoffRepository.java` | Not in the design's data model. Justified by "instances stateless, safe to run twice". Add it to `design.md`. |
| `items_story_status_check` and the 300-character `story_note` limit | `V2__stories.sql:19-21` | Not in the design. Harmless tightening. |
| `DailyBudget` implemented as `WriterBudgetRepository` | `stories/persistence/WriterBudgetRepository.java` | Name and package differ from the design. |
| `StoryServiceTest` implemented as `StoryServiceIT` | `StoryServiceIT.java` | Uses the database, so the `IT` suffix is correct. Update the design. |
| Extra config keys `tick`, `max-backoff`, `base-url` | `application.yml:116-117`, `AnthropicConfig.java:277` | Not in the design's config contract. Reasonable. Document them. |
| "No model configured" treated as unavailable | `AnthropicStoryWriter.java:40-42` | Not requested. Sensible. Document it. |
| Live output goes to `target/`, not `build/` | `StoryWriterLiveIT.java:141` | The design says `build/live-samples.md`. `tasks.md` already uses the Maven path. |
| Section `description` added in 002 T1 | `Section.java` | Built for 003 R1.1 ahead of time. Minor scope creep, now used by 003. |
| Continuous writing replaced by the daily batch; `StoryService.applyDraft` split; request building moved into `StoryRequests` | `StoryService.java:112-138`, `StoryRequests.java`, `SchedulingConfig.java:65-83` | Superseded by 006 (R6.4 keeps `realtime` mode). Not drift. |
| Non-functional "published within 15 minutes" | — | Superseded by 006. |

No other scope creep was found in the 002 commits.

## Resolution (2026-10-04)
All blocking items above were fixed with tests, in two rounds (`fix(…)`, `test(…)`, `refactor(…)` commits on 2026-10-04), along with most minor items. Deb approved the design changes in the session, and they are written up under "Approved changes (2026-10-04)" in this spec's design.md and requirements.md. Full check after the fixes:
- Backend `mvn verify`: BUILD SUCCESS, 151 unit tests and 162 integration tests.
- `npm run verify`: passed, including the client drift check (api-client 6 tests, web 65 tests).
- Browser tests: 65 passed, 7 skipped (each skip is a test meant for the other screen size).
Still open: the T7 live sample run against real feeds, which needs a machine that can reach the news sites. The spec can be marked done only after it runs.
