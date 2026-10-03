# 004 — AI Latest feed: Tasks

Each task is 2–4 hours, leaves the full check green, and can be tested on its own.

## T1 — `hours` window in the API
- **Covers:** R1.1–R1.4
- **Depends on:** 003 T2
- **Do:** `hours` param with validation in `CatalogController`; window passed through `CatalogService`; update `openapi.json` with `-Dqbits.updateOpenApi=true`; regenerate the client.
- **Tests:** `CatalogControllerIT` cases for the 24 h boundary, combination with `section` and `cursor`, `INVALID_HOURS` (0, 73, `abc`); `OpenApiContractIT`.
- **Done when:** `GET /api/v1/stories?hours=24` returns only the last 24 hours and the contract test passes.
- **Status:** done

## T2 — Client: `hours`, latest feed and new-stories hooks
- **Covers:** R3.2, R5.1, R5.2, R5.4
- **Depends on:** T1
- **Do:** `hours` in `StoriesQuery` and `useStories`; `useLatestFeed`; `useNewStories(topId)` with 2-minute polling paused when hidden.
- **Tests:** `client.test.ts` (sends `hours`); `useNewStories.test.tsx` with MSW and fake timers (count, "20+", no polling while hidden, one check on becoming visible).
- **Done when:** `npm run verify --workspace @qbits/api-client` passes.
- **Status:** done

## T3 — Routes and tab order
- **Covers:** R2.1–R2.4
- **Depends on:** T2
- **Do:** Move `HomePage` to `/sections` as `SectionsOverviewPage`; `/` renders a placeholder `LatestPage`; "AI Latest" first in `SectionNav`; footer link "Browse by section".
- **Tests:** `SectionNav.test.tsx` (order, AI Latest current on `/`, section tab current on `/section/india-ai`); `AppRoutes.test.tsx` (`/sections` renders the overview; old routes still work).
- **Done when:** all existing web tests pass with the new routes.
- **Status:** done

## T4 — Feed card, source badge and clamped text
- **Covers:** R4.1–R4.5
- **Depends on:** T3
- **Do:** `SourceBadge` with 8 contrast-checked colour tokens; `ClampedText`; `FeedCard`.
- **Tests:** `FeedCard.test.tsx` (part order, badge letter and stable colour, Show more/Show less with `aria-expanded`, Words to know only when expanded, link-out attributes, no `img`/`iframe`/social buttons); `SourceBadge.test.tsx` (same name → same colour).
- **Done when:** all pass.
- **Status:** done

## T5 — AI Latest page
- **Covers:** R3.1–R3.4
- **Depends on:** T4
- **Do:** `LatestPage` with heading, updated line, infinite list with Load more fallback, skeleton feed cards, empty state with "Browse by section", error with retry.
- **Tests:** `LatestPage.test.tsx` (lists `hours=24` stories, loads more with cursor, empty state, error and retry).
- **Done when:** all pass.
- **Status:** done

## T6 — New stories button
- **Covers:** R5.1–R5.5
- **Depends on:** T5
- **Do:** `NewStoriesButton` in a polite live region; wire `useNewStories`; apply = reload from top and scroll up.
- **Tests:** `LatestPage.test.tsx` new-stories flow with fake timers: no button at first; after a newer story appears at the next check, "1 new story" shows; clicking it puts the story at the top; list never changes before the click.
- **Done when:** all pass.

## T7 — End-to-end and accessibility
- **Covers:** R2.2, R4.5, non-functional
- **Depends on:** T6
- **Do:** Update e2e mock API for `hours`; `latest.spec.ts` at 360 px and 1280 px; update `home.spec.ts` and `a11y.spec.ts` for `/` and `/sections`; keep the bundle budget.
- **Tests:** the Playwright suite.
- **Done when:** no horizontal scroll, no axe violations in light and dark, budget met.
