# 004 — AI Latest feed: Review

Reviewed: 2026-10-04 · Reviewer role: `agents/reviewer.md` · Scope: all of 004 (T1–T7)

## Verdict: CHANGES REQUESTED

The feature is well built and almost every criterion is covered by a test that would fail without the code. Two items block sign-off: one criterion (R4.5) has no test for its font-size part, and `design.md` was changed during implementation without re-approval. Both are small to fix. Everything else is minor.

## Gate
- `.status` reads `tasks:approved`, set in `042aeac chore: approve specs 004 and 005 (approved by Deb)` (2026-10-03 09:16), before the first 004 code commit (`5684b00`, 16:56). Gate passed.

## Commits reviewed
| Commit | Task |
|---|---|
| `5684b00` | T1 — `hours` window on the stories API |
| `1e92a32` | T2 — latest feed and new-stories hooks in the API client |
| `98a1fbb` | T3 — AI Latest as the first tab; overview moves to `/sections` |
| `8fef355` | T4 — feed card, source badge, expandable summary (also edits `design.md`) |
| `87d2c0d` | T5 — AI Latest page: infinite feed, empty and error states |
| `37d61c1` | T6 — new-stories button |
| `a422ba1` | T7 — browser and accessibility checks; fix to 003 sticky phone tabs |
| `52102bf` | (no task) — About page mentions AI Latest |

Later commits from 006 (`f278883`, `f2f1a3d`) changed parts of this work. Those changes are treated as **superseded by 006**, not as failures or drift.

## Full check (recorded results, 2026-10-04)
| Check | Result |
|---|---|
| Backend `mvn -f backend/pom.xml verify` | BUILD SUCCESS — 116 unit tests, 115 integration tests, Spotless clean |
| Web/client `npm run verify` | Passed — api-client 6 tests, web 54 tests, type-check, lint, Prettier |
| Playwright e2e (phone 360 px + desktop 1280 px, axe included) | 44 passed, 2 skipped (phone-only and desktop-only cases skipped on the other project) |

The contract change (`hours` query parameter) is in `packages/api-client/openapi.json` and `src/schema.ts`, and `OpenApiContractIT` passes, so the generated client is up to date.

## Acceptance criteria

Summary: **15 PASS · 0 FAIL · 1 NO TEST · 6 SUPERSEDED by 006** (22 total)

| Id | Status | Code | Test |
|---|---|---|---|
| R1.1 | PASS (window end since re-anchored by 006 R4.2) | `backend/src/main/java/com/qbits/api/CatalogController.java:96-116`, `backend/src/main/java/com/qbits/catalog/CatalogService.java:49-56` | `CatalogControllerIT.hoursLimitsTheWindow` |
| R1.2 | PASS | `CatalogController.java:100-116` | `CatalogControllerIT.invalidHoursIsProblem400` (0, 73, `abc`, `1.5`) |
| R1.3 | PASS | `CatalogService.java:53-60` (cursor encodes position only, per design) | `CatalogControllerIT.hoursCombinesWithSectionAndCursor` |
| R1.4 | PASS | `/sections` unchanged | `CatalogControllerIT.sectionCountsStay72Hours`, `listsSectionsInOrderWithCounts` |
| R2.1 | PASS | `web/src/features/sections/SectionNav.tsx:22-26` | `SectionNav.test.tsx` "lists the sections from the API in order"; e2e `latest.spec.ts` "opens on AI Latest…" |
| R2.2 | PASS | `web/src/app/AppRoutes.tsx` (index → `LatestPage`), `SectionNav.tsx` (`end`) | `SectionNav.test.tsx` "marks AI Latest as current on the home page"; `AppRoutes.test.tsx` "shows AI Latest at / …" |
| R2.3 | PASS | `AppRoutes.tsx` (`/sections`), `web/src/app/AppShell.tsx` footer, `LatestPage.tsx:94-101` | `AppRoutes.test.tsx` "has a footer link to browse by section"; `LatestPage.test.tsx` load-more test (end-of-feed link) and empty-state test |
| R2.4 | PASS | `AppRoutes.tsx` | `SectionPage.test.tsx`, `StoryPage.test.tsx`, `AboutPage.test.tsx` (all through `AppRoutes`); e2e `a11y.spec.ts` |
| R3.1 | SUPERSEDED by 006 R5.3 | "Updated <time>" replaced by the edition line; heading and fallback line remain at `LatestPage.tsx:36-49` | was `LatestPage.test.tsx` (87d2c0d); now 006 edition tests |
| R3.2 | PASS | `LatestPage.tsx:19-31, 82-93`; `packages/api-client/src/hooks.tsx:52-55` | `LatestPage.test.tsx` "lists the last 24 hours and loads more with the cursor"; `hooks.test.tsx` "sends the hours window" |
| R3.3 | PASS | `LatestPage.tsx:64-74` | `LatestPage.test.tsx` "shows the empty state with a way to browse sections" |
| R3.4 | PASS | `LatestPage.tsx:62-63`, `web/src/features/latest/FeedSkeleton.tsx` | `LatestPage.test.tsx` "shows feed skeletons while loading", "shows a plain error with a working retry" |
| R4.1 | PASS | `web/src/features/latest/FeedCard.tsx:20-39`, `SourceBadge.tsx` | `FeedCard.test.tsx` "shows the parts in timeline order"; `SourceBadge.test.tsx` (letter, no image, stable colour) |
| R4.2 | PASS | `FeedCard.tsx:40-59` | `FeedCard.test.tsx` "clamps the summary and expands it with the words to know"; e2e "a card expands in place…" |
| R4.3 | PASS | `FeedCard.tsx:61-64` | `FeedCard.test.tsx` "links out safely and shows the attribution" |
| R4.4 | PASS | `FeedCard.tsx` | `FeedCard.test.tsx` "has no images, embeds or social buttons"; e2e `img, iframe` count 0 |
| R4.5 | **NO TEST** (partial) | `FeedCard.tsx:35` (`text-[17px]`), `FeedCard.tsx:43` (`text-base`) | No horizontal scroll is checked at 360 px (`web/e2e/latest.spec.ts:20-23`), but no test asserts headline ≥ 17 px or summary ≥ 16 px. Changing either class would not fail any test. |
| R5.1 | SUPERSEDED by 006 R5.4 | polling removed in `f2f1a3d` | was `hooks.test.tsx` "waits two minutes…" |
| R5.2 | SUPERSEDED by 006 R5.4 | button removed | was `NewStories.test.tsx`, e2e "new stories wait behind a button…" |
| R5.3 | SUPERSEDED by 006 R5.4 | button removed | was `NewStories.test.tsx` (scroll-to-top itself was never asserted) |
| R5.4 | SUPERSEDED by 006 R5.4 | polling removed | was `hooks.test.tsx` "does not check while the page is hidden" |
| R5.5 | SUPERSEDED by 006 R5.4 | button removed | was `NewStories.test.tsx` (polite live region) |

Non-functional: the bundle budget is still enforced by the 003 bundle-size test (passing). The 2.5 s interactive target has no automated check; this is the same as 003 and not counted as a criterion.

## Principle violations (blocking)

1. **Process — every criterion has a test.** R4.5's minimum font sizes are not tested (see table). `web/src/features/latest/FeedCard.tsx:35,43`; no assertion in `web/e2e/latest.spec.ts`. Fix: in the phone project, read the computed `font-size` of the first card's headline and summary and assert ≥ 17 px and ≥ 16 px.
2. **Process — change the spec first, get it re-approved, then change the code.** Commit `8fef355` (T4) edited the approved `specs/004-ai-latest-feed/design.md` at the same time as the code, with no re-approval: `design.md:26` (new `useNewStories(firstPage, loadedAt)` signature) and `design.md:43` (`ClampedText` dropped). `tasks.md:16` and `tasks.md:32` still describe the old approach (`useNewStories(topId)`, `ClampedText`), so the spec now contradicts itself. Fix: the owner either re-approves the two design notes (and `tasks.md` is aligned) or the notes are reverted and recorded here as drift. The `useNewStories` part is now moot (removed by 006); the `ClampedText` decision still stands in the code.

No content, legal, fetching or architecture principle is broken: cards show source, time, "Read the full story" and the attribution; there are no images, logos, embeds or social controls; the window is computed in the backend and the client only passes `hours`; no publisher text is logged or stored by this change.

## Convention issues

Minor:
- **Requirement references in tests.** Backend tests carry `// 004 Rx.y` comments, but no web, client or e2e test does (`web/src/features/latest/*.test.tsx`, `web/src/features/sections/SectionNav.test.tsx`, `web/src/app/AppRoutes.test.tsx`, `packages/api-client/src/hooks.test.tsx`, `web/e2e/latest.spec.ts`). `steering/conventions.md` asks for them. Same gap exists in 003's web tests.
- **Boundary test.** Design asks for a test at exactly 24 hours; `CatalogControllerIT.hoursLimitsTheWindow` uses 23 h and 25 h. The query is inclusive (`published_at >= :windowStart`, `StoryQueryRepository.java:43`), so behaviour is right, but the edge is not pinned.
- **Test file names differ from tasks/design.** `useNewStories.test.tsx` was built as `packages/api-client/src/hooks.test.tsx`; the T6 flow was put in `NewStories.test.tsx` rather than `LatestPage.test.tsx`. Coverage was equivalent. Both now superseded.
- **Swallowed exception (historical).** `NewStoriesButton.tsx:30` (`37d61c1`) had an empty `catch` around `window.scrollTo`. It was a deliberate, commented recovery and the file was removed by 006.

Checked and fine: API shape and versioning (additive query parameter under `/api/v1`), Problem Details with `code: INVALID_HOURS`, client calls only through `packages/api-client`, TanStack Query for server state, file naming, link-out attributes (`target="_blank" rel="noopener noreferrer"`), responsive at 360 px and 1280 px (e2e), axe clean in light and dark on `/` and `/sections`. Source badge colours (`web/src/index.css:14-21`) all reach at least 5.5:1 against white text (computed), above AA.

## Drift and unrequested work

Drift:
- **`useLatestFeed` without `refetchOnWindowFocus: false`.** Design said the feed would not refetch on focus so the list never jumps; `packages/api-client/src/hooks.tsx:52-55` (as built in `1e92a32`) leaves the default on with a 60 s stale time. Under 004 this could reload the list when a reader returned to the tab. **Superseded by 006 R5.4**, which asks for a refresh when the tab is reopened, so no action needed.
- **`apply()`** used `resetQueries` instead of the designed `invalidateQueries`. Equivalent effect; superseded by 006.
- **`ClampedText`** not built; the clamp lives in `FeedCard`. See blocking item 2.
- **R1.3 cursor reuse.** The requirement says a cursor "stays valid only with the same `hours` and `section`"; the design (approved) chose not to enforce this, so a cursor sent with different `hours` is accepted. Matches the design, noted for the owner.

Unrequested work (minor; owner to confirm):
- **003 sticky-tab fix in T7** (`a422ba1`): `web/src/app/AppShell.tsx:26` and `web/src/features/sections/SectionNav.tsx:19`. A real bug fix with an e2e test ("the tab bar stays at the top while scrolling on phones"), and recorded in `tasks.md` T7. It is outside any 004 task; `CLAUDE.md` says work outside a spec needs the owner's say-so. Please confirm it is accepted.
- **About page text** (`52102bf`, `web/src/features/about/AboutPage.tsx`): no task covers it, though the design's Risks section mentions an About note. No test was added for the new text. The wording has since been replaced by 006 ("today's edition").

## Superseded by 006 (for the record, not failures)
- The "N new stories" check, button, polling and live region (R5.1–R5.5, T6, parts of T2 and T7) were removed in `f2f1a3d` per 006 R5.4.
- AI Latest's "Updated <relative time>" line (R3.1) became the edition line ("Today's digest · published <time>", "Next edition around <time>") per 006 R5.3.
- The 24-hour window is now measured from the latest edition's cut-off rather than from the server clock (006 R4.2, `CatalogService.java:55`). The `hours` parameter, its validation and its tests from T1 are unchanged.

## Resolution (2026-10-04)
All blocking items above were fixed with tests, in two rounds (`fix(…)`, `test(…)`, `refactor(…)` commits on 2026-10-04), along with most minor items. Deb approved the design changes in the session, and they are written up under "Approved changes (2026-10-04)" in this spec's design.md and requirements.md. Full check after the fixes:
- Backend `mvn verify`: BUILD SUCCESS, 151 unit tests and 162 integration tests.
- `npm run verify`: passed, including the client drift check (api-client 6 tests, web 65 tests).
- Browser tests: 65 passed, 7 skipped (each skip is a test meant for the other screen size).
