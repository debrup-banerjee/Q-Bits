# 003 — News reader: Review

- **Date:** 2026-10-04
- **Scope:** all of spec 003 (T1–T9), commits `3b9cc05` … `ff09bc3`, read against the code as it stands today (`a0ce8ff`).
- **Gate:** `specs/003-news-reader/.status` reads `tasks:approved`. Note: the status arrived already approved in the first repository commit (`f5422b5`); there is no separate approval commit for 003 as there is for 004–006.
- **Later specs:** 004 (AI Latest first, overview moved to `/sections`, `hours` window) and 006 (windows anchored to the latest edition, `X-Data-As-Of` = edition publish time, live "new stories" check removed) changed 003 behaviour with approval. Those changes are marked "superseded by NNN" below and are not counted as drift.

## Verdict: CHANGES REQUESTED

One criterion fails (R4.2), three criteria and one non-functional target have no test, one Process principle was broken (design changed during implementation without a recorded re-approval), and the About page hard-codes section names.

## Full check (recorded today, not re-run for this review)

| Check | Result |
|---|---|
| `mvn -f backend/pom.xml verify` | BUILD SUCCESS: 116 unit tests, 115 integration tests, Spotless clean |
| `npm run verify` | Passed: api-client 6 tests, web 54 tests, type-check, lint, Prettier |
| Playwright e2e (phone 360px + desktop 1280px, axe included) | 44 passed, 2 skipped (both skips are by design: bundle check runs on desktop only, sticky-tab check on phone only) |
| Lighthouse mobile | Not run: needs a deployment |

## Acceptance criteria

| ID | Status | Code | Test |
|---|---|---|---|
| R1.1 | PASS | `api/CatalogController.java` `sections()`; `stories/domain/Section.java` (order, names, descriptions) | `CatalogControllerIT.listsSectionsInOrderWithCounts`; `StoryQueryRepositoryIT.countsBySectionAndReportsLatest` (minor: `description` is not asserted; only the OpenAPI contract test would catch its removal) |
| R2.1 | PASS (window anchor superseded by 006 R4.2) | `catalog/persistence/StoryQueryRepository.java:38-77`; `catalog/CatalogService.java` `list()` | `StoryQueryRepositoryIT.includesExactly72HoursAndExcludesOlder`, `showsOnlyPublishedVisibleStories`, `ordersNewestFirstAndFiltersBySection`, `pagesWithCursorWithoutGapsOrRepeats`; `CatalogControllerIT.pagesWithCursor`, `windowsAreAnchoredToTheLatestEditionCutoff` |
| R2.2 | PASS | `StoryQueryRepository.list()` (section filter optional) | `StoryQueryRepositoryIT.ordersNewestFirstAndFiltersBySection` |
| R2.3 | PASS | `catalog/domain/StoryView.java`; `CatalogService.view()` | `CatalogControllerIT.listsStoriesWithClientFields`; `StoryQueryRepositoryIT.viewHasOurFieldsAndNeverTheSourceText` |
| R2.4 | PASS | `StoryView` has no title/excerpt fields; SELECT does not read them | `CatalogControllerIT.listsStoriesWithClientFields` (raw JSON has no "SOURCE TITLE"/"SOURCE EXCERPT"); `StoryQueryRepositoryIT.viewHasOurFieldsAndNeverTheSourceText` |
| R2.5 | PASS | `CatalogController.stories()`; `api/ProblemAdvice.java` | `CatalogControllerIT.unknownSectionIsProblem400` |
| R2.6 | PASS (window superseded by 006 R4.2) | `CatalogController.story()`; `StoryQueryRepository.find()` | `CatalogControllerIT.singleStoryOr404` (minor: only the "older than 72 h" case is tested; "hidden" and "never existed" are not) |
| R2.7 | PASS (`X-Data-As-Of` value superseded by 006 R5.2) | `CatalogController.ok()`; `CatalogService.dataAsOf()` | `CatalogControllerIT.sendsCacheAndDataAsOfHeaders`, `editionEndpointAndDataAsOf` |
| R3.1 | NO TEST | `CatalogController.java:128-133`; `CatalogService.java:90-91` (`registry.enabled()`) | `CatalogControllerIT.listsEnabledSourcesAndSiteContact` checks the two fields only. The API test sources file (`one-enabled.yml`) has a single enabled source, so the test still passes if `enabled()` is replaced by `all()`. The "enabled only" half has no test. |
| R4.1 | PASS (page moved to `/sections` by 004 R2.3) | `web/src/features/sections/SectionsOverviewPage.tsx` | `SectionsOverviewPage.test.tsx` "shows the four sections with their newest stories"; e2e `home.spec.ts` |
| R4.2 | FAIL | `web/src/features/sections/SectionsOverviewPage.tsx:21-29` | The line "AI news from the last 72 hours" is now shown only when `X-Data-As-Of` is missing; once data loads the page shows "Browse by section" + "Updated …" and no 72-hour line. It was correct at the end of 003 (`HomePage.tsx` h1). Changed in `98a1fbb` (004/T3), which was asked only to move the page (004 R2.3, 004 design "renamed SectionsOverviewPage"). The test was changed to match (`SectionsOverviewPage.test.tsx:41`, `:100`). "Updated" source is superseded by 006 R5.2. |
| R4.3 | PASS (fixed and tested in 004/T7) | `web/src/app/AppShell.tsx:26-28`; `SectionNav.tsx` | `SectionNav.test.tsx`; e2e `latest.spec.ts` "the tab bar stays at the top while scrolling on phones", `home.spec.ts` "section navigation is visible and usable". Note: at 003 hand-off the phone bar did not stick (sticky element inside a non-sticky wrapper) and no test caught it. |
| R5.1 | PASS | `web/src/features/sections/SectionPage.tsx` | `SectionPage.test.tsx` "lists a section and loads more pages with the cursor" (minor: the scroll trigger itself, `IntersectionObserver`, is not exercised in any browser test; the "Load more" fallback is) |
| R5.2 | PASS | `web/src/components/EmptyState.tsx` | `SectionPage.test.tsx` "shows the empty message for a quiet section"; e2e `home.spec.ts` |
| R6.1 | PASS | `web/src/features/stories/StoryCard.tsx`; `components/RelativeTime.tsx` | `StoryCard.test.tsx` "shows every part of the story", "says \"about\" when the publish date was estimated"; `RelativeTime.test.ts` |
| R6.2 | PASS | `web/src/features/stories/WordsToKnow.tsx` | `WordsToKnow.test.tsx` (collapsed on phone width with names only, one tap opens, open on wide) |
| R6.3 | PASS | `web/src/components/ExternalLink.tsx` | `StoryCard.test.tsx` "links out safely to the publisher"; e2e `home.spec.ts` "overview → section → the link out" |
| R6.4 | PASS | `StoryCard.tsx:44` | `StoryCard.test.tsx` "shows every part of the story" |
| R6.5 | PASS | `StoryCard.tsx` | `StoryCard.test.tsx` (no `img, iframe, video, embed, object`); e2e `home.spec.ts` |
| R6.6 | PASS | `web/src/features/stories/StoryPage.tsx` | `StoryPage.test.tsx`; e2e `home.spec.ts` "a story page can be opened directly" |
| R7.1 | PASS | `web/src/features/about/AboutPage.tsx:14-26` | `AboutPage.test.tsx` "explains how stories are made" |
| R7.2 | PASS | `AboutPage.tsx:54-76` | `AboutPage.test.tsx` "lists the sources from the API" |
| R7.3 | PASS | `GET /api/v1/site` (`CatalogController.java:135`), `application.yml` `qbits.contact-email`; `AboutPage.tsx:78-92` | `CatalogControllerIT.listsEnabledSourcesAndSiteContact`; `AboutPage.test.tsx` "gives the contact address" |
| R8.1 | PASS | `web/src/components/SkeletonCard.tsx` | `SectionsOverviewPage.test.tsx` "shows skeleton cards while loading" (minor: section and story pages not tested) |
| R8.2 | PASS | `web/src/components/ErrorState.tsx` | `SectionsOverviewPage.test.tsx` "shows a plain error with a working retry" (checks no code is shown and retry works) |
| R9.1 | NO TEST | `SectionsOverviewPage.tsx:11` (1/2/3 columns), `SectionPage.tsx:42` | No horizontal scroll is tested at 360px and 1280px (`home.spec.ts`, `latest.spec.ts`). Nothing tests the column counts (one / two / three) or the 1440px upper bound; removing `sm:grid-cols-2 lg:grid-cols-3` fails no test. |
| R9.2 | NO TEST | `StoryCard.tsx:38` (`text-base`, `max-w-[65ch]`) | No test checks the 16px summary size or the line-length cap. |
| R9.3 | PASS | `web/src/index.css` (tokens, dark mode, `:focus-visible`); skip link in `AppShell.tsx` | e2e `a11y.spec.ts` (axe WCAG 2 A/AA on all five pages, light and dark); `SectionNav.test.tsx` "is reachable by keyboard" (minor: visible focus is not asserted) |

### Non-functional

| Target | Status | Notes |
|---|---|---|
| Home interactive < 2.5 s on a mid-range phone over 4G | NO TEST | Lighthouse not run (needs a deployment). T9 says the same. The 120 KB gzipped bundle budget (`web/e2e/bundle.spec.ts`) is a useful guard but not a substitute. |
| No cookies, analytics or third-party scripts | PASS (inspection) | `web/index.html` loads only the app bundle; system fonts; no cookies set. No automated guard. |
| Web talks to the backend only through `packages/api-client` | PASS (inspection) | No `fetch` or hard-coded API URLs in `web/src`; all data via `@qbits/api-client` hooks. |

**Counts (28 criteria + 3 non-functional = 31):** PASS 26, FAIL 1, NO TEST 4.

## Principle violations (blocking)

1. **Process: spec changed alongside the code, no re-approval recorded.** `specs/003-news-reader/design.md:16` (and the `CorsConfig` → `WebConfig` rename on line 15) was edited in the same commit as the code (`4114497`, 003/T2) to add `GET /api/v1/site`, marked "Added during implementation". `principles.md` (Process) requires the spec to change first and be re-approved before the code. The endpoint itself is sound and needed for R7.3; the owner needs to confirm the design change (for example an approval commit like the ones for 004–006).

No other principle violations found. Checked: API returns no source title or excerpt (R2.4 tests); no images, logos or embeds; every card shows source, time, "Read the full story" link and attribution; links use `target="_blank" rel="noopener noreferrer"`; the 72-hour window lives in the backend (now edition-anchored per 006); disabled sources are hidden through `items.hidden` (001) and the read queries filter on it; reads never call the summary service; no personal data; no excerpts in logs (`ProblemAdvice` logs only a correlation id and the exception).

## Convention issues

### Blocking
1. **Section names hard-coded in the client.** `web/src/features/about/AboutPage.tsx:36-51` writes out all four section names and descriptions. `conventions.md` (Sections): display names are defined once in the backend and returned by `/api/v1/sections`; "clients never hard-code them". The texts have already drifted from the API ("new AI models and products, and what they can do" vs "New AI models, products and what they can do."). Use `useSections()`.

### Minor
1. **No criterion references in web tests.** Backend tests carry `// 003 Rx.y` comments; none of the web unit or e2e tests do (`conventions.md` Testing: "Reference it in a comment").
2. **List endpoints without the envelope.** `/api/v1/sections` and `/api/v1/sources` return bare arrays; `conventions.md` REST shape describes lists as `{ "data": [...], "nextCursor": ... }`. Fine for short fixed lists, but it should be a recorded decision; changing it later is a breaking change.
3. **Generated client not checked against `openapi.json`.** `OpenApiContractIT` guards `openapi.json` against the live API, but nothing fails if `packages/api-client/src/schema.ts` is not regenerated. Today the paths and schemas match by inspection.
4. **Hand-edited nullability.** `CatalogService.java:101` can return `homepage: null` (source removed from config) while the OpenAPI schema says string; `packages/api-client/src/client.ts:16` patches the type by hand. In practice such stories are hidden by `SourceVisibility`, so the case should not be reachable; either mark the field nullable in the contract or drop the fallback.
5. **Catch-all maps client mistakes to 500.** `api/ProblemAdvice.java:36` turns framework errors such as an unsupported method or malformed request into 500 `INTERNAL` with an ERROR log and stack trace. Those should be 4xx.
6. **"about yesterday".** `components/RelativeTime.tsx:37` prefixes "about" to `Intl` output, giving "about yesterday" for estimated dates 24–36 h old.
7. **Package layout.** `CatalogService` sits in `catalog` rather than `catalog.service`, and `CatalogController` uses `catalog.domain.CursorCodec` directly. Consistent with the rest of the repo, but not with the letter of `conventions.md` (Backend code layout).

## Drift from design (minor)

- `SectionCatalog` (design table) does not exist; its job is split between `Section` (enum with descriptions) and `CatalogService`/`CatalogController`.
- Query hooks live in `packages/api-client/src/hooks.tsx`, not `web/src/hooks/` as the design's tree shows. T3 asked for hooks in the client package and the tech stack wants them shared with mobile, so the design tree should be updated rather than the code.
- No `StoryList` component; list rendering is inline in `SectionPage` and the overview page.
- Theme tokens are defined in `web/src/index.css` (`@theme`, Tailwind 4), not `tailwind.config`. Same intent, different file.
- `CatalogControllerIT` uses `Instant.now()`, not a fixed `Clock` as the design's testing section says. The exact 72-hour boundary is covered with a fixed clock in `StoryQueryRepositoryIT` instead.
- T9 named `section.spec.ts`; the section flow lives in `home.spec.ts`. T9 also used Playwright `page.route` instead of MSW in the browser; this is recorded in `tasks.md` T9.

## Unrequested work

- `GET /api/v1/site`: needed for R7.3 and recorded in the design, but see Principle violation 1.
- Extra error codes `INVALID_CURSOR`, `INVALID_LIMIT`, `INVALID_PARAMETER`, `NOT_FOUND`: edge validation the conventions ask for; fine.
- Skip-to-content link and the "Load more" button fallback on section pages: accessibility support for R9.3 and R5.1; fine.
- The open question ("Words of the day" strip) was not built. Correct.

## To reach APPROVE

1. Restore the "AI news from the last 72 hours" line on `/sections` next to "Updated …", and test both together (`SectionsOverviewPage.tsx:21-29`).
2. Add a disabled source to the API test sources and assert it is not listed by `/api/v1/sources` (R3.1).
3. Add browser checks for R9.1 (column count at phone, tablet and desktop widths, and no horizontal scroll at 1440px) and R9.2 (summary computed font size ≥ 16px and width ≤ ~75ch).
4. Run Lighthouse mobile on a deployment and record the result in T9.
5. Owner confirms the `/api/v1/site` design change (Principle violation 1).
6. Read section names and descriptions on the About page from `/api/v1/sections`.

## Resolution (2026-10-04)
All blocking items above were fixed with tests, in two rounds (`fix(…)`, `test(…)`, `refactor(…)` commits on 2026-10-04), along with most minor items. Deb approved the design changes in the session, and they are written up under "Approved changes (2026-10-04)" in this spec's design.md and requirements.md. Full check after the fixes:
- Backend `mvn verify`: BUILD SUCCESS, 151 unit tests and 162 integration tests.
- `npm run verify`: passed, including the client drift check (api-client 6 tests, web 65 tests).
- Browser tests: 65 passed, 7 skipped (each skip is a test meant for the other screen size).
Still open: the Lighthouse check, which needs a deployment. Minor items not done: the bare-list responses on `/sections` and `/sources`, and framework errors returning 500.
