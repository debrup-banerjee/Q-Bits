# 005 — Open-source links: Review

**Verdict: CHANGES REQUESTED**

Reviewed on 2026-10-04 against `requirements.md`, `design.md`, `tasks.md` and all of `steering/`.

## Scope reviewed
- Gate: `.status` set to `tasks:approved` in `042aeac` ("approve specs 004 and 005 (approved by Deb)"), before any 005 code. Gate passed.
- Commits: `a5a5e12` (T1), `a6930e5` (T2), `d48ae5f` (T3), `cb4dfb8` (T4), `0ef9705` (T5), `4b8ec48` (T6), `2254dba` (T7), plus `034745b` (README note).
- Later commits from spec 006 (`24fd95d`..`f2f1a3d`) changed `CatalogService` windows and the web feed. Those changes are treated as superseded by 006, not as drift in 005.

## Full check (recorded results from today)
| Check | Result |
|---|---|
| Backend `mvn -f backend/pom.xml verify` | BUILD SUCCESS: 116 unit tests, 115 integration tests, Spotless clean |
| Web `npm run verify` | Passed: api-client 6 tests, web 54 tests, type-check, lint, Prettier |
| Playwright e2e (phone 360 px, desktop 1280 px, axe included) | 44 passed, 2 skipped |

## Acceptance criteria
Totals: **22 PASS, 0 FAIL, 0 NO TEST.**

| Id | Status | Code | Test |
|---|---|---|---|
| R1.1 | PASS | `ingestion/FeedParser.java` `links()` (entry links, description anchors and plain URLs, content anchors) | `FeedParserTest.collectsLinksFromEntryDescriptionAndContentWithoutText` |
| R1.2 | PASS | `FeedParser.links()` returns URLs only; `RawEntry.links` / `FoundLink(url, origin)` | `FeedParserTest.collectsLinksFromEntryDescriptionAndContentWithoutText` (no "FULL ARTICLE" in entry); `IngestionJobIT.storesOpenSourceCandidatesAndNeverTheContentText` (no content text in `items`, `item_resources`, `source_fetch_log`) |
| R1.3 | PASS | `V4__item_resources.sql`; `ItemResourceRepository.insertIfNew`; `ResourceCollector.collect`; `SourceIngestor.insert` | `IngestionJobIT.storesOpenSourceCandidatesAndNeverTheContentText`; `ItemResourceRepositoryIT.storesEachLinkOncePerItem` |
| R2.1 | PASS | `ResourceLinkNormaliser.normalise` host switch (lines 104–110) | `ResourceLinkNormaliserTest.dropsEverythingElse` (other hosts, gist, ftp); `normalisesAllowlistedProjectLinks` (`www.`, `export.` rows) |
| R2.2 | PASS | `ResourceLinkNormaliser` `github`, `gitlab`, `huggingFace`, `hfRepo`, `arxiv`, `arxivId` | `ResourceLinkNormaliserTest.normalisesAllowlistedProjectLinks` (every design table row plus case, `.git`, query, fragment, old-style arXiv id) |
| R2.3 | PASS | `GITHUB_RESERVED`, `GITLAB_RESERVED`, `HF_RESERVED`, two-segment minimum | `ResourceLinkNormaliserTest.dropsEverythingElse` |
| R2.4 | PASS | `ResourceCollector` `seen` set; `unique (item_id, url)` | `ItemResourceRepositoryIT.storesEachLinkOncePerItem` |
| R3.1 | PASS | `ResourceRelevance.keep` | `ResourceRelevanceTest.keepsLinksFromEntryLinksAndDescription` |
| R3.2 | PASS | `ResourceRelevance.keep` (whole-word, 3+ characters) | `ResourceRelevanceTest.keepsContentLinksOnlyWhenTheProjectIsNamed`, `matchesWholeWordsOnly`; `IngestionJobIT.storesOpenSourceCandidatesAndNeverTheContentText` (share widget dropped) |
| R4.1 | PASS | `HostApis` `github`, `gitlab`, `huggingFace`, `arxiv`; `ResourceCheckJob.run`; `ItemResourceRepository.findDue` | `HostApisIT.githubFoundMissingAndRenamed`, `gitlabAndHuggingFace`, `arxivFoundAndMissing` |
| R4.2 | PASS (see blocking B1) | `HostApis.github` reads `full_name`; `ResourceCheckJob.apply` | `HostApisIT.githubFoundMissingAndRenamed` (301 rename); `ResourceCheckJobIT.usesTheRenamedGitHubRepo` |
| R4.3 | PASS | `HostApis.byStatus`; `ResourceCheckJob.apply` → `markNotFound`; `findDue` excludes `NOT_FOUND` | `ResourceCheckJobIT.missingLinksAreNeverCheckedAgain`; `HostApisIT.gitlabAndHuggingFace` (401 gated) |
| R4.4 | PASS | `ResourceCheckJob.retryOrGiveUp`; `retry-delays` config | `ResourceCheckJobIT.retriesAfter10Minutes1Hour6HoursThenGivesUp`; `HostApisIT.githubRateLimitAndTimeoutAreFailures` |
| R4.5 | PASS | `LinkCheckCacheRepository.findFresh`/`save`; `cache-ttl: PT24H` | `ResourceCheckJobIT.verifiesAndSharesTheResultAcrossItems`; `ItemResourceRepositoryIT.cacheIsFreshFor24Hours` |
| R4.6 | PASS | `HostLimiter.tryAcquire`; `rate-limits` in `application.yml`; shared `feedRestClient` User-Agent | `HostApisIT.ownLimiterThrottlesWithoutCallingTheHost`, `sendsTokenOnlyWhenConfiguredAndAlwaysTheUserAgent`; `ResourceCheckJobIT.throttledHostStopsForThisRunOnly` |
| R4.7 | PASS | `CheckResult.Found(canonicalName)` only; schema holds url, name, status | `HostApisIT.githubFoundMissingAndRenamed` (extra `description` field ignored) |
| R5.1 | PASS | `ResourceQueries.verifiedFor`; `CatalogService.list`/`find`; `StoryView.resources` | `CatalogControllerIT.includesVerifiedOpenSourceLinksInOrderAndAtMostThree`, `storiesWithoutLinksHaveAnEmptyList`; `OpenApiContractIT.committedContractMatchesTheApi` |
| R5.2 | PASS | `Candidate.label()`; `ResourceLink` | `CatalogControllerIT.includesVerifiedOpenSourceLinksInOrderAndAtMostThree`; `ResourceLinkNormaliserTest` (label column, incl. "Demo on Hugging Face", "Code on GitLab", "Paper on arXiv") |
| R6.1 | PASS | `web/src/components/ResourceLinks.tsx`; used in `StoryCard.tsx:40`, `FeedCard.tsx:60` | `ResourceLinks.test.tsx` "lists each link with its label and name" (name in `<code>`); `StoryCard.test.tsx` "shows open-source links when present" |
| R6.2 | PASS | `ResourceLinks.tsx` `target`, `rel`, `aria-label` | `ResourceLinks.test.tsx`; `e2e/open-source.spec.ts` "feed cards show open-source links without expanding" |
| R6.3 | PASS | `ResourceLinks.tsx` returns `null` when empty; row outside the expandable part of `FeedCard` | `ResourceLinks.test.tsx` "renders nothing when there are no links"; `FeedCard.test.tsx` "shows open-source links without expanding the card", "shows no open-source row when there are no links" |
| R6.4 | PASS | Stories are read without any link gate; resources joined at read time | `CatalogControllerIT.includesVerifiedOpenSourceLinksInOrderAndAtMostThree` (story with a still-pending link is served, pending link omitted) |

Responsive and accessibility: `e2e/open-source.spec.ts` checks no horizontal overflow and runs axe (WCAG 2 A/AA, light and dark) on both Playwright projects (360 px and 1280 px).

Editorial style: this spec does not change the summary prompt or any generated text. Labels are fixed strings (`Candidate.label()`), so there are no summaries to sample.

## Blocking

**B1. A GitHub rename can collide with another link on the same item and stall all link checks.**
`backend/src/main/java/com/qbits/resources/ResourceCheckJob.java:111-116` rewrites the row's URL to `https://github.com/{full_name}` through `ItemResourceRepository.markVerified` (`backend/src/main/java/com/qbits/resources/persistence/ItemResourceRepository.java:67-79`). If the same item already holds that URL, the update breaks `unique (item_id, url)` (`V4__item_resources.sql:16`).
Scenario: an entry links both `github.com/OpenAI/whisper` and `github.com/openai/whisper` (case is kept by design, so both are stored), or both an old and a renamed repo. The second row's update throws. The exception leaves `run()`, the row stays `PENDING` with the oldest `next_check_at`, and the cache now hits it before any host call. So every later run throws at the same row, and every candidate queued behind it goes unchecked until that item leaves the 72-hour window.
Fix: when the canonical URL already exists for the item, mark this row `VERIFIED` without changing its URL (or mark it a duplicate) instead of updating. Catch per-row failures so one row cannot end the run. Add a `ResourceCheckJobIT` case for it.

**B2. The approved design was changed after approval without re-approval (Process principle).**
Commit `2254dba` (T7) added "Notes from implementation" to `specs/005-open-source-links/design.md:120-124` while `.status` stayed `tasks:approved`. Those notes record departures from the approved design: host values stored upper case instead of `('github','gitlab','huggingface','arxiv')` (`design.md:39`, implemented at `V4__item_resources.sql:7`), and a new cap of 10 candidates per item (`ResourceCollector.java:21`). A further cap of 50 links per entry (`FeedParser.java:32`) is not recorded anywhere. `steering/principles.md` (Process): "If code and spec disagree, change the spec first, get it re-approved, then change the code."
Fix: the owner reviews these notes, including the 50-link cap, and re-approves the design, or the code goes back to the approved design.

## Principle check
| Principle | Result |
|---|---|
| Links only from URLs in the feed entry, URLs only, no surrounding text | Met (`FeedParser.links`; `IngestionJobIT` leak check) |
| Allowlisted hosts only; never generated, guessed or searched | Met (`ResourceLinkNormaliser`) |
| Checked through official APIs, within limits, honest User-Agent, cached daily | Met (`HostApis`, `HostLimiter`, `LinkCheckCacheRepository`) |
| Nothing stored from host responses beyond status and canonical name | Met |
| Checking lives in the backend; clients only display | Met |
| One failing source never blocks others / ingestion unaffected by host errors | Met for ingestion. B1 shows one bad row can block all other link checks. |
| Approved spec is the source of truth | Not met: B2 |
| Every acceptance criterion has a test | Met |

## Convention issues (minor)
1. `FoundLink` and `LinkOrigin` live in `resources.domain` but are imported by `ingestion/domain/RawEntry.java:3` and `ingestion/FeedParser.java:4-5`. Ingestion's domain now depends on another feature's domain. The design placed the link pair with `RawEntry` in `ingestion.domain`.
2. Task T1 asked for `LinkCheckCacheRepositoryIT`. It was not created; the cache test is `ItemResourceRepositoryIT.cacheIsFreshFor24Hours`.
3. `HostApis.java:52` logs a failed host check at INFO. Conventions put a degraded dependency at WARN. The design's "Logs carry host, status and item id" is not met: neither this line nor the run summary at `ResourceCheckJob.java:96-102` names the item.
4. `packages/api-client/openapi.json:43-61`: `ResourceLink.type` is a free string, not an enum of `code|model|dataset|paper`. Clients cannot rely on the generated type.
5. `ResourceQueries.java:15` Javadoc says "at most 3 each"; the code caps at 3 in total, which is correct per R5.1.
6. R5.1 is tested with resources only on `GET /api/v1/stories/{id}`. The list endpoint is tested only with an empty list.
7. `link_check_cache` has no clean-up, so rows build up without limit. It holds URLs only, so this is housekeeping, not a principle issue.

## Drift and unrequested work (minor)
1. `ResourceLinkNormaliser.java:50-54` adds `resources`, `solutions`, `team`, `contact`, `github` to the approved list of reserved GitHub segments. `github` drops real repos owned by the GitHub organisation (for example `github.com/github/gitignore`). `HF_RESERVED` adds `huggingface` at line 82, which drops models published under that organisation. This fails safe (links are dropped, never wrong), but it differs from the design.
2. `ResourceLinks.tsx:3` adds type glyphs (`</>`, `◆`, `▦`, `¶`) that no task asked for. They are text and `aria-hidden`, so they break no image or logo rule.
3. Long names are shortened in the pill (`ResourceLinks.tsx:30`), with the full name in `title` and the accessible label. This was recorded only in the T7 status line in `tasks.md`, not in the design.
4. Spec wording conflict: R4.4 says "retry up to 3 times", but `design.md:118` says "after 3 failures → NOT_FOUND". The code follows R4.4 (four calls in total, as `ResourceCheckJobIT` shows). The design line should be corrected when B2 is handled.
5. README note on `GITHUB_TOKEN` and reachable hosts (`034745b`): documentation only, acceptable.

## Superseded by 006 (not drift)
- `CatalogService` windows are now anchored to the latest edition (006/T5). 005's resource join is unchanged and still covers both list and single-story reads.
- `ResourceCheckJob` still limits checks to items published in the last 72 hours of clock time (`ResourceCheckJob.java:30`). This decides which links get checked, not which stories are shown, so 006's edition windows do not apply to it.

## Resolution (2026-10-04)
All blocking items above were fixed with tests, in two rounds (`fix(…)`, `test(…)`, `refactor(…)` commits on 2026-10-04), along with most minor items. Deb approved the design changes in the session, and they are written up under "Approved changes (2026-10-04)" in this spec's design.md and requirements.md. Full check after the fixes:
- Backend `mvn verify`: BUILD SUCCESS, 151 unit tests and 162 integration tests.
- `npm run verify`: passed, including the client drift check (api-client 6 tests, web 65 tests).
- Browser tests: 65 passed, 7 skipped (each skip is a test meant for the other screen size).
