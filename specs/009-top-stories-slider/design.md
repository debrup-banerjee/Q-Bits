# 009 — Top stories slider: Design

## Overview
Coverage is derived, not stored: one grouped query counts, for each published story in the AI
Latest window, the distinct sources among the story and the `DUPLICATE` rows that point to it
(R1.1, R1.2). `GET /api/v1/stories/top` returns the best three with coverage of 2 or more (R2).
The web app renders them as a native scroll-snap slider above the AI Latest feed (R3). No summary
service, prompt, model or new table is involved, so there is no token cost.

## Components
| Component | Package | Responsibility | Requirements |
|---|---|---|---|
| `TopStoryRules` | `catalog.domain` | Pure: filter coverage >= `minCoverage`, order by coverage desc, official first, earliest `publishedAt`, id; take `limit` | R2.1, R2.2 |
| `StoryQueryRepository.coverage` | `catalog.persistence` | One query: published stories in the window with `1 + count(distinct duplicate source) ` and source id | R1.1, R1.2 |
| `CatalogService.top` | `catalog` | Window from `editions.windowEnd() - 24h`, applies `TopStoryRules`, builds `StoryView`s | R2.1, R2.3, R2.4 |
| `CatalogController` (changed) | `api` | `GET /api/v1/stories/top`; `Cache-Control` and `X-Data-As-Of` as for the list | R2.3 |
| `TopStoryView` | `api`/`catalog.domain` | `StoryView` fields plus `coverage` | R2.3 |
| `TopStories` | `web/src/features/latest` | Slider: scroll container with snap, prev/next, dots, slide labels | R3.1-R3.6 |
| `useTopStories` | `web/src/features/latest` | TanStack Query hook through `packages/api-client` | R3.4 |

`catalog` reads duplicates through `stories`/`ingestion` services only if it must; the query lives
in `StoryQueryRepository`, which already reads `items`.

## Data model
No new table or column. One index, `V12__top_stories_index.sql`:
```sql
create index items_duplicate_of_idx on items (duplicate_of) where duplicate_of is not null;
```
Coverage query (published stories in the window, with duplicate sources):
```sql
select i.id, i.source_id,
       1 + count(distinct d.source_id) filter (where d.source_id <> i.source_id) as coverage
from items i
left join items d on d.duplicate_of = i.id and d.story_status = 'DUPLICATE'
where i.story_status = 'PUBLISHED' and i.published_at >= :from
group by i.id, i.source_id
having 1 + count(distinct d.source_id) filter (where d.source_id <> i.source_id) >= :min;
```
(Distinct sources: a second copy from the same source does not add coverage.)

## Contracts
`GET /api/v1/stories/top` -> `{ "data": [TopStoryView x 0..3] }` (no cursor). Registered beside
`/stories/{id}`; the literal path wins. Headers: `X-Data-As-Of`, `Cache-Control: public,
max-age=300`. The OpenAPI contract and `packages/api-client` are regenerated (README steps).

Config:
```yaml
qbits:
  top-stories:
    enabled: true
    min-coverage: 2
    limit: 3
```
`enabled: false` returns an empty list (off switch).

## Flow
1. Page load: client requests `/stories/top` once with the feed's first page.
2. Backend computes the 24-hour window start from the latest edition (as `hours=24` does), runs
   the coverage query, applies `TopStoryRules`, and returns up to 3 stories.
3. Client renders slides. Empty list -> nothing rendered (R3.4). The feed below is unchanged
   (R3.6). The 2-minute "new stories" check does not refetch the slider.

## Error handling
- Query or rules failure: 500 Problem Details as for any endpoint; the client hides the slider and
  shows the feed normally (the slider never blocks the page).
- Duplicate rows whose kept story was deleted by retention have `duplicate_of` set null by spec
  008, so they stop counting; no special case.

## Testing approach
| Requirement | Test |
|---|---|
| R2.1, R2.2 | `TopStoryRulesTest`: coverage below 2 excluded; order by coverage, official, earliest, id; limit 3; fewer than 3 returned |
| R1.1-R1.3 | `StoryQueryRepositoryIT`: a story with 2 duplicate sources has 3; two duplicates from one source count once; same read twice is equal; a later duplicate raises coverage |
| R2.3, R2.4 | `TopStoriesApiIT`: shape, headers, no `DUPLICATE` rows, window respected, `enabled: false` empty |
| R3.1-R3.6 | `TopStories.test.tsx`: renders up to 3 slides with labels "1 of 3"; no autoplay; hidden when empty; no images; prev/next and keyboard; `aria-roledescription` |
| R3.5 | Playwright at 360px and 1280px: no horizontal page scroll; text sizes |
| OpenAPI | Contract regenerated and committed (`-Dqbits.updateOpenApi=true`) |

## Alternatives considered
- **Store coverage on each story at publish.** Goes stale when a later edition holds back more
  copies of an already-published story, so rejected for derived data.
- **Ask the model to rank importance.** Costs tokens every day and is inconsistent.
- **Click counts.** Needs reader tracking and traffic we don't have yet.
- **Slider library (Swiper etc.).** Adds weight and dependencies for what scroll-snap does natively.

## Risks
- **Few stories may reach 2 outlets on a quiet day**, so the slider can have one or two stories or
  none. That is the honest outcome of the threshold; `min-coverage` is configurable.
- **Coverage depends on spec 008 grouping quality.** A false merge could inflate coverage; the
  strict grouper prompt and held-back audit trail from 008 limit this.
