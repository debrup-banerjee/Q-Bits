# 009 — Top stories slider: Tasks

Each task is 2–4 hours, leaves the full check green, and can be tested on its own.

## T1 — Coverage query and ranking rules
- **Covers:** R1.1, R1.2, R1.3, R2.1, R2.2
- **Depends on:** none (spec 008 shipped)
- **Do:** `V12__top_stories_index.sql`; `StoryQueryRepository.coverage`; pure `TopStoryRules`;
  `qbits.top-stories.*` config record (`enabled`, `min-coverage` 2, `limit` 3).
- **Tests:** `TopStoryRulesTest`, `StoryQueryRepositoryIT` additions; `ApplicationStartsIT`.
- **Done when:** `mvn -f backend/pom.xml verify` passes.

## T2 — Top stories endpoint
- **Covers:** R2.3, R2.4
- **Depends on:** T1
- **Do:** `CatalogService.top`; `TopStoryView`; `GET /api/v1/stories/top` with cache headers;
  regenerate OpenAPI and `packages/api-client`.
- **Tests:** `TopStoriesApiIT`; contract file updated.
- **Done when:** the endpoint returns up to 3 stories with coverage and never a duplicate.

## T3 — Slider on AI Latest
- **Covers:** R3.1, R3.2, R3.3, R3.4, R3.5, R3.6
- **Depends on:** T2
- **Do:** `TopStories` component (scroll-snap, prev/next, dots, labels, no autoplay) and
  `useTopStories` hook in `web/src/features/latest`; mount above the feed; hide when empty or on
  error.
- **Tests:** `TopStories.test.tsx`; Playwright checks at 360px and 1280px.
- **Done when:** `npm run verify` and `npm run e2e --workspace web` pass.
