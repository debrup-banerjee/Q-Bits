# 003 — News reader (API + responsive web): Tasks

Each task is 2–4 hours, leaves the full check green, and can be tested on its own.

## T1 — Story queries and cursor
- **Covers:** R2.1, R2.2, R2.4
- **Depends on:** 002 T1
- **Do:** `StoryQueryRepository` (keyset SQL with 72 h window), `CursorCodec`, `StoryView` mapping.
- **Tests:** `StoryQueryRepositoryIT` (window boundary, exclusions, ordering, paging); `CursorCodecTest`.
- **Done when:** queries proven against Testcontainers Postgres.
- **Status:** done

## T2 — Catalog API and errors
- **Covers:** R1.1, R2.3, R2.5, R2.6, R2.7, R3.1
- **Depends on:** T1
- **Do:** `SectionCatalog`, `CatalogController`, `ProblemAdvice`, cache headers, `X-Data-As-Of`, CORS, springdoc; commit `openapi.json` and a contract test.
- **Tests:** `CatalogControllerIT`, `OpenApiContractTest`.
- **Done when:** all endpoints match the design examples.
- **Status:** done

## T3 — Web workspace and API client
- **Covers:** non-functional (client-only access)
- **Depends on:** T2
- **Do:** npm workspaces at repo root; `packages/api-client` generated from `openapi.json` (`openapi-typescript` + `openapi-fetch`) with query hooks; `web/` Vite + React + TS strict + Tailwind + ESLint/Prettier + Vitest + MSW; `npm run verify`.
- **Tests:** `api-client` smoke test against MSW; empty app renders.
- **Done when:** `npm run verify --workspace web` passes.
- **Status:** done

## T4 — Layout, theme and section navigation
- **Covers:** R4.3, R9.1, R9.3 (theme, focus)
- **Depends on:** T3
- **Do:** App shell, header, footer with About link, `SectionNav` (sticky tabs on phones, top bar on wide), light/dark tokens, routes.
- **Tests:** `SectionNav.test.tsx` (active state, keyboard), router test for unknown routes.
- **Done when:** nav works at 360px and 1280px.
- **Status:** done

## T5 — Story card and words to know
- **Covers:** R6.1–R6.5
- **Depends on:** T3
- **Do:** `StoryCard`, `WordsToKnow`, `ExternalLink`, `RelativeTime`.
- **Tests:** `StoryCard.test.tsx` (all fields, "about" for estimated dates, link attrs and label, no `<img>`), `WordsToKnow.test.tsx` (collapsed on phone width).
- **Done when:** all pass.
- **Status:** done

## T6 — Home page
- **Covers:** R4.1, R4.2, R8.1, R8.2
- **Depends on:** T4, T5
- **Do:** `HomePage` with four section grids (5 newest each), "last 72 hours" line, "Updated …", skeletons, error state.
- **Tests:** `HomePage.test.tsx` with MSW: data, loading, error with retry.
- **Done when:** all states render correctly.
- **Status:** done

## T7 — Section page and story page
- **Covers:** R5.1, R5.2, R6.6, R8
- **Depends on:** T6
- **Do:** `SectionPage` with infinite scroll, empty state; `StoryPage`.
- **Tests:** `SectionPage.test.tsx` (paging, empty), `StoryPage.test.tsx` (404 → friendly message).
- **Done when:** all pass.
- **Status:** done

## T8 — About page
- **Covers:** R7.1–R7.3
- **Depends on:** T4, T2 (`/sources`)
- **Do:** `AboutPage` with plain-language explanation, live source list, takedown contact from config.
- **Tests:** `AboutPage.test.tsx`.
- **Done when:** sources list renders from API.
- **Status:** done

## T9 — End-to-end, accessibility and performance
- **Covers:** R9.1–R9.3, non-functional
- **Depends on:** T6, T7, T8
- **Do:** Playwright suite at 360px and 1280px against MSW-backed preview; axe checks; bundle-size budget; Lighthouse mobile run noted in the task log.
- **Tests:** `home.spec.ts`, `section.spec.ts`, `a11y.spec.ts`.
- **Done when:** no horizontal scroll, no axe violations, home interactive < 2.5 s in Lighthouse mobile profile.
- **Status:** done, except the Lighthouse run. No horizontal scroll at 360px and 1280px, no axe violations in light or dark mode, JS bundle within 120 KB gzipped. The API is mocked with Playwright's page.route rather than MSW in the browser, so no mock service worker ships with the app. Lighthouse still to be run once the app is deployed.
