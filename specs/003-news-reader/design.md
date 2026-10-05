# 003 — News reader (API + responsive web): Design

## Overview
The backend's `catalog` package serves read-only JSON from `items` joined to `stories`, filtered to `PUBLISHED`, not hidden and the last 72 hours. springdoc publishes the OpenAPI document, from which `packages/api-client` is generated. The React app in `web/` uses that client with TanStack Query and renders three page types plus About, styled mobile-first with Tailwind.

## Backend components
| Component | Package | Responsibility | Requirements |
|---|---|---|---|
| `SectionCatalog` | `catalog` | Section metadata (slug, name, description) and 72 h counts. | R1 |
| `StoryQueryRepository` | `catalog.persistence` | SQL for list (keyset on `published_at, id`) and single story. | R2 |
| `StoryView` | `catalog.domain` | API record; built only from story fields + source name/homepage + URL. | R2.3, R2.4 |
| `CursorCodec` | `catalog.domain` | Opaque base64url cursor of `(publishedAt, id)`. | R2.1 |
| `CatalogController` | `api` | `/api/v1/sections`, `/stories`, `/stories/{id}`, `/sources`; cache headers. | R1–R3 |
| `ProblemAdvice` | `api` | RFC 9457 errors with `code`. | R2.5, R2.6 |
| `WebConfig` | `api` | Allow configured web origins, GET only; expose `X-Data-As-Of`. | — |
| `GET /api/v1/site` | `api` | Site name and contact email for the About page. *Added during implementation:* the web app has no other way to read `qbits.contact-email`. | R7.3 |

### Section descriptions (served by API)
- **Global AI Tech** — "New AI models, products and what they can do."
- **World Business** — "Money, companies, chips and jobs around the world: where AI meets the economy."
- **India AI** — "Everything AI in India: companies, startups, government, research and global players' moves in India."
- **Innovations & Research** — "New ideas and discoveries from labs and universities."

### Window
`now()` comes from the injected `Clock`; window start = now − 72 h. Query:
```sql
select ... from items i join stories s on s.item_id = i.id
where i.story_status = 'PUBLISHED' and not i.hidden
  and i.published_at >= :windowStart
  and (:section is null or s.section = :section)
  and (i.published_at, i.id) < (:cursorAt, :cursorId)
order by i.published_at desc, i.id desc
limit :limit + 1;
```

## API contract (summary; OpenAPI is authoritative)
```json
GET /api/v1/stories?section=india-ai&limit=20
200 { "data": [ {
  "id": "0192f0c4-...",
  "section": { "slug": "india-ai", "name": "India AI" },
  "headline": "India opens its first public AI computing hub for startups",
  "summary": "...",
  "keyTerms": [ { "term": "GPU", "meaning": "A chip that does many small calculations at once, ideal for AI." } ],
  "source": { "name": "The Hindu", "homepage": "https://www.thehindu.com/sci-tech/technology/" },
  "originalUrl": "https://www.thehindu.com/...",
  "publishedAt": "2026-10-03T04:10:00Z",
  "dateEstimated": false,
  "attribution": "Summary written from The Hindu's headline and teaser"
} ], "nextCursor": "eyJ0IjoiMjAy..." }
```

## Web app
```
web/src/
  app/            router, query client, layout (header, section nav, footer)
  features/
    stories/      StoryCard, WordsToKnow, StoryList (infinite), StoryPage
    sections/     HomePage (section grids), SectionPage, SectionNav
    about/        AboutPage
  components/     SkeletonCard, ErrorState, EmptyState, RelativeTime, ExternalLink
  hooks/          useSections, useStories(section), useStory(id)
```
- Routes: `/`, `/section/:slug`, `/story/:id`, `/about`.
- Data: `useInfiniteQuery` for lists (`staleTime` 60 s, refetch on window focus); `X-Data-As-Of` surfaced via the client's response hook.
- Layout: Tailwind breakpoints `sm` (≥640) two columns, `lg` (≥1024) three columns for home grids; section pages stay single-column for reading. Max prose width `65ch`.
- `ExternalLink` always sets `target="_blank" rel="noopener noreferrer"` and an accessible label "Read the full story at {source} (opens in a new tab)".
- `WordsToKnow`: `<details>` element, closed by default under `sm`, open at `sm` and above.
- Theme: CSS variables for light/dark via `prefers-color-scheme`; tokens defined once in `tailwind.config`.
- Relative time with `Intl.RelativeTimeFormat`; "about" prefix when `dateEstimated`.

## Error handling
API errors become Problem Details; the client maps any non-2xx to `ErrorState` with a retry calling `refetch()`. Unknown `/section/:slug` routes render a friendly not-found page.

## Testing approach
- Backend: `CatalogControllerIT` (Testcontainers + fixed `Clock`): window boundary at exactly 72 h, hidden/NOT_AI/REJECTED excluded, ordering, cursor paging, unknown section 400, story 404, no source excerpt in JSON, cache headers.
- Contract: a test that the generated OpenAPI document matches the committed `openapi.json`, so client regeneration is never forgotten.
- Web: Vitest + Testing Library with MSW for each component and page state (loading, empty, error, data); Playwright at 360px and 1280px for home → section → external link; axe accessibility check in Playwright.

## Alternatives considered
- Server-side rendering (Next.js) — not needed for a team reader (tech-stack).
- Offset pagination — unstable while new stories arrive; keyset cursor is stable.

## Risks
- Generated-client drift — mitigated by the contract test.
- Long key-term lists on phones — capped at 5 and collapsed by default.

## Approved changes (2026-10-04)
Approved by Deb in session after the spec reviews. Where this section and the text above differ, this section wins.

- `GET /api/v1/site` (site name, contact) and the `CorsConfig` → `WebConfig` rename are part of the design.
- Dates: relative time with `Intl.RelativeTimeFormat`; when `dateEstimated`, numeric times get an "about" prefix ("about 5 hours ago") and word forms an "(estimated)" suffix ("yesterday (estimated)").
- `source.homepage` is `null` when the source is no longer on the approved list; the contract marks it nullable.
- The /sections overview always shows "AI news from the last 72 hours, sorted into four sections", with "Updated …" beside it when known.
- The About page reads section names and descriptions from the API.
