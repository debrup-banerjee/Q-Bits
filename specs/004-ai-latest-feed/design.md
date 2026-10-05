# 004 — AI Latest feed: Design

## Overview
The backend gains an `hours` parameter on the stories list; everything else in the read model stays. The web app gets a new `LatestPage` at `/`, a compact `FeedCard`, and a `useNewStories` hook that polls the first page in the background and shows a "N new stories" button instead of reshuffling the list. The old `HomePage` moves to `/sections` unchanged apart from its route and a heading tweak.

## Backend changes
| Component | Change | Requirements |
|---|---|---|
| `CatalogController.stories` | New `hours` param (Integer, optional). Validate 1–72 → `INVALID_HOURS`. | R1.1, R1.2 |
| `CatalogService.list` | Take a `Duration window` (default 72 h); `windowStart = now − window`. | R1.1 |
| `CursorCodec` | Unchanged. Cursors encode position only; they are valid within any window that contains that position. Clients always re-send the same `hours`/`section`. | R1.3 |
| `sections`, `story/{id}`, `dataAsOf` | Unchanged; still 72 h. | R1.4 |

Contract: `openapi.json` gains the `hours` query parameter; regenerate `packages/api-client`.

```
GET /api/v1/stories?hours=24&limit=20
GET /api/v1/stories?hours=24&limit=20&cursor=…
GET /api/v1/stories?hours=0          → 400 { "code": "INVALID_HOURS" }
```

## API client changes
- `StoriesQuery` gains `hours?: number`.
- `useStories(section?, limit, hours?)` adds `hours` to the query key.
- New `useLatestFeed()` = `useStories(undefined, 20, 24)` with `refetchOnWindowFocus: false` (the new-stories hook handles freshness so the list never jumps).
- New `useNewStories(firstPage, loadedAt)` (*as built*: takes the feed's first page so the first check waits 2 minutes instead of firing at once):
  - `useQuery(['stories','latest-check'], () => api.stories({ hours: 24, limit: 20 }), { refetchInterval: 120_000, refetchIntervalInBackground: false })`
  - Returns the stories in that page that come before `topId` (by position). Count = their number; at 20 it shows "20+".
  - `apply()` = invalidate `['stories','all',20,24]` so the feed reloads from the top, then scroll to the top.

## Web components
```
web/src/features/latest/
  LatestPage.tsx      heading, updated line, NewStoriesButton, list, sentinel/Load more, empty state
  FeedCard.tsx        compact card (R4)
  SourceBadge.tsx     letter-in-circle; colour = one of 8 token colours chosen by a stable hash of the source name
  NewStoriesButton.tsx sticky pill inside an aria-live="polite" region
  useNewStories.ts    (or in the api-client package, so mobile can reuse it)
web/src/features/sections/
  HomePage.tsx        → renamed SectionsOverviewPage, route /sections
  SectionNav.tsx      first link "AI Latest" → "/", end match
web/src/components/
  (clamp lives inside FeedCard: it also controls Words to know, so a separate ClampedText added nothing)
```
- Feed card layout: badge (40 px) left; right column with "Source · time · Section" line, headline (`text-[17px] font-semibold` on phones, 18 px at `sm`), clamped summary (`line-clamp-3`), actions row with the link out. Cards separated by a 1 px divider rather than boxed, to read as a timeline; max width `36rem`, centred.
- Words to know render only when expanded (R4.2), reusing `WordsToKnow` with `startsOpen=true`.
- Source badge colours are 8 accent tokens added to the `@theme` block, each checked for AA contrast with white text in light and dark modes.

## Routes
| Path | Page |
|---|---|
| `/` | LatestPage (new) |
| `/sections` | SectionsOverviewPage (was HomePage) |
| `/section/:slug`, `/story/:id`, `/about` | unchanged |

## Error handling
Background checks fail silently (the next check retries); only the main list shows the error state. `INVALID_HOURS` cannot occur from the UI because `hours` is a constant.

## Testing approach
- Backend: extend `CatalogControllerIT` (24 h boundary at exactly 24 h, combination with section and cursor, `INVALID_HOURS` for 0, 73, `abc`); update `OpenApiContractIT` contract.
- Client: `client.test.ts` sends `hours`; `useNewStories` unit test with MSW (counts, "20+", pause when hidden via `document.visibilityState`).
- Web: `FeedCard.test.tsx` (order of parts, clamp and expand with `aria-expanded`, no images or social controls), `LatestPage.test.tsx` (list, load more, empty state with link, error/retry, new-stories button flow with fake timers), `SectionNav.test.tsx` (new order, AI Latest current on `/`).
- E2E: update `home.spec.ts` for the new landing page; add `latest.spec.ts` (360 px and 1280 px, no horizontal scroll, expand a card, new-stories button after a mocked newer story); axe on `/` and `/sections` in light and dark.

## Alternatives considered
- Server-sent events or websockets for live updates: more moving parts for a list that changes every few minutes. Polling one small page every 2 minutes is enough.
- Auto-inserting new stories at the top: makes the list jump while reading. The button pattern is what timeline apps use.
- Making AI Latest a fifth `Section`: breaks the one-section-per-story rule and the section counts.

## Risks
- With 24 sources and a 30-minute fetch interval, a story can appear up to ~35 minutes after publication (fetch + summary). Acceptable; noted on the About page as "updated through the day".
- A quiet day may leave the feed short. The empty state and "Browse by section" link cover it.

## Approved changes (2026-10-04)
Approved by Deb in session after the spec reviews. Where this section and the text above differ, this section wins.

- The notes added in commit `8fef355` (the `useNewStories` signature and dropping `ClampedText`) are approved. Both were later superseded by spec 006, which removed the new-stories button and polling.
- The phone tab-bar fix done in T7 and the About page change are accepted.
