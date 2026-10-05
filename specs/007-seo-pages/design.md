# 007 — Search-ready pages: Design

## Overview
Spring Boot serves the first HTML response for app routes. At startup it reads the `index.html`
that Vite built (the "shell"), and for each request it fills that shell with the page's metadata,
JSON-LD, readable content inside `#root`, and the API data the page was built from (R1, R2, R4).
React then starts as today, seeds TanStack Query from the embedded data, and replaces the
server markup with its own render; there is no hydration. Static files stay with nginx (R8).

```
browser ──GET /story/abc──▶ nginx ──(not a file)──▶ Spring PageController
                                                     ├─ CatalogService.find(id)
                                                     ├─ PageRenderer(shell, PageModel) → HTML
                                                     └─ 200 / 404 + Cache-Control
browser ──GET /assets/x.js──▶ nginx (web/dist)
browser (app running) ──GET /api/v1/...──▶ nginx ──▶ Spring API (unchanged)
```

## Components
| Component | Package | Responsibility | Requirements |
|---|---|---|---|
| `PagesProperties` | `com.qbits.pages` | `qbits.pages.*` config: site origin, shell file path | R2.3, R1.6 |
| `PageShell` | `com.qbits.pages` | Loads `index.html` once at startup; falls back to a built-in minimal shell (and logs WARN) if the file is missing, so tests and local runs work without a web build | R1.6 |
| `PageModel` | `com.qbits.pages.domain` | Record: status, title, description, canonical path, og type, robots, JSON-LD list, body HTML, initial-data JSON | R1–R3 |
| `PageRenderer` | `com.qbits.pages.domain` | Pure: shell + `PageModel` → HTML string. Replaces `<title>` and the description meta, inserts head tags before `</head>`, body into `<div id="root">` | R1.6, R2, R4.1 |
| `PageTitles` | `com.qbits.pages.domain` | Title strings and fixed descriptions | R2.1, R2.2 |
| `Html` | `com.qbits.pages.domain` | Pure: text and attribute escaping; JSON-in-script escaping (`<` → `<`, plus U+2028/2029) | R2.7 |
| `PageContent` | `com.qbits.pages.domain` | Pure: builds body HTML for story, story list, section overview from `StoryView`/section data; `describe()` cuts summary to 160 chars at a word | R1.1–R1.4, R1.7, R2.2 |
| `JsonLd` | `com.qbits.pages.domain` | Pure: builds `NewsArticle`, `WebSite`, `Organization` maps | R2.5, R2.6 |
| `Sitemaps` | `com.qbits.pages.domain` | Pure: robots.txt text, sitemap and news sitemap XML | R6 |
| `PageController` | `com.qbits.pages.api` | `@Controller` for page routes and catch-all 404; builds `PageModel`s from `CatalogService`/`EditionQueries`; serialises initial data with the app's JSON mapper | R1–R3, R7.1 |
| `CrawlController` | `com.qbits.pages.api` | `/robots.txt`, `/sitemap.xml`, `/sitemap-news.xml` | R6, R7.2 |
| `initial-data.ts` (`seedInitialData`) | `web/src/app/` | Reads `#qbits-initial-data`, checks it matches `location.pathname`, seeds the query client before the first render (`main.tsx`) | R4.2–R4.4 |
| `usePageTitle` | `web/src/hooks/` | Sets `document.title` per page | R5.1 |
| `page-titles.ts` | `web/src/app/` | Title strings, kept identical to the backend's (both tested against `titles.json` here) | R2.1, R5.1 |
| nginx config | `deploy/aws/nginx-qbits.conf`, `qbits-proxy.conf` | Routing, www redirect, backend-down fallback | R8 |

`PageController` uses `CatalogService` and `EditionQueries` only, as the API controllers do
(conventions: features talk through services).

## Data model
None. No migration.

## Contracts

### Config
```yaml
qbits:
  pages:
    site-url: ${QBITS_SITE_URL:https://qbitsnews.com}     # canonical origin, no trailing slash
    shell-file: ${QBITS_SHELL_FILE:../web/dist/index.html} # prod: /opt/qbits/app/web/dist/index.html
```
`QBITS_SHELL_FILE` and `QBITS_SITE_URL` are added to `deploy/aws/env.example`.

### Routes (all `GET`, `text/html; charset=utf-8` unless noted)
| Path | Data used | Status | Initial data (key → API shape) |
|---|---|---|---|
| `/` | `catalog.list(empty, empty, 20, 24h)`, `editions.latestPublished()` | 200 | `stories:all:20:24` → `StoryPage`; `edition` → `EditionView` (omitted if no edition) |
| `/sections` | `counts()`, `list(section, empty, 5, 72h)` per section | 200 | `sections` → `SectionView[]` |
| `/section/{slug}` | `Section.fromSlug`, `list(section, empty, 20, 72h)` | 200 / 404 | `sections`, `stories:<slug>:20:72` |
| `/story/{id}` | `catalog.find(id)` | 200 / 404 | `story:<id>` → `StoryView` |
| `/about` | none | 200 | none |
| `/login`, `/register` | none | 200, `noindex` | none |
| anything else not matched above or by `/api/**`, `/v3/**`, `/actuator/**` | none | 404, `noindex` | none |
| `/robots.txt` | none | 200 `text/plain` | — |
| `/sitemap.xml` | `list(empty, …)` paged to the end, 72h | 200 `application/xml` | — |
| `/sitemap-news.xml` | same, filtered to `publishedAt` ≥ now − 48h | 200 `application/xml` | — |

The section overview mirrors what the web page fetches today (`useSections` + one `useStories(slug, 5)` per section); its keys are `stories:<slug>:5:72`.

### Initial data element
```html
<script id="qbits-initial-data" type="application/json">
{"path":"/story/0192…",
 "queries":[{"key":["story","0192…"],"infinite":false,"data":{…StoryView…},
             "dataAsOf":"2026-10-05T00:42:00Z"}]}
</script>
```
`key` is the exact TanStack query key the hook uses. `dataAsOf` is per query: the
`X-Data-As-Of` value the matching API response would carry (the edition's publish time for
`["edition"]`). For infinite queries (`stories`) the client wraps the entry as
`{pages:[{data, dataAsOf}], pageParams:[undefined]}`; other entries become `{data, dataAsOf}`, the
`WithAsOf<T>` shape `api-client` returns. `["edition"]` is seeded only when an edition exists.

### Head tags inserted (story example)
```html
<title>OpenAI ships … | Q-Bits</title>
<meta name="description" content="…160 chars…">
<link rel="canonical" href="https://qbitsnews.com/story/0192…">
<meta property="og:type" content="article"> … og:title, og:description, og:url, og:site_name
<meta name="twitter:card" content="summary">
<script type="application/ld+json">{"@context":"https://schema.org","@type":"NewsArticle",…}</script>
```

### Body markup
Semantic HTML using the same element structure and main Tailwind classes as `StoryCard` /
`FeedCard` / `SectionPage`, inside the same outer layout wrapper, so the swap to React's render is
visually close. It does not include the app header/nav (React renders `AppShell`); a minimal
`<header>` with a link to `/` and the section links is included so crawlers find internal links.

## Flow

### Main path (`/story/{id}`)
1. nginx: not a file in `web/dist`, proxies to `127.0.0.1:8080` with `Host`, `X-Forwarded-Proto`.
2. `PageController` parses the UUID (failure → 404 page) and calls `catalog.find(id)`.
3. Found → builds `PageModel` via `PageContent` and `JsonLd`, serialises `StoryView` as initial data.
4. `PageRenderer.render(shell, model)` → HTML. Response 200, `Cache-Control: public, max-age=300`.
5. Browser parses HTML, shows server content, loads the bundle. `main.tsx` calls
   `bootstrap(queryClient)` before `createRoot().render()`. Seeded `['story', id]` means
   `StoryPage` renders the card on first render, no request.

### Failure paths
- Story missing/hidden/expired or bad id → 404 shell with "Story not available | Q-Bits", `noindex`,
  `no-store`. The web app shows its existing "This story isn't available" page after its own 404
  from the API.
- Database error → `PageController` catches `DataAccessException` around data loading, logs ERROR
  with the path, and returns the plain shell with 503 and `no-store`; the app then shows its usual
  error state. (nginx replaces a backend 503 with the static shell, which is equivalent.)
- Non-GET requests and unknown `/api/`, `/v3/`, `/actuator` paths that reach the catch-all get the
  usual JSON Problem Details 404, never HTML.
- Shell file missing at startup → WARN log, built-in minimal shell (no bundle). In prod, deploy
  copies `dist` before restarting the backend, so this only happens on a broken deploy.
- Backend down → nginx `error_page 502 503 504 = /index.html` serves the static shell (R8.4).
- Initial data malformed or for another path → `bootstrap` ignores it (R4.3).

## nginx
See `deploy/aws/nginx-qbits.conf`. In short: `www.qbitsnews.com` → 301 to `https://qbitsnews.com`;
`/assets/*` and `/favicon.svg` from `web/dist` (assets cached a year, immutable); `/api/` and
`/` proxied to `127.0.0.1:8080`; `/actuator` and `/v3/` answer 404 (they were never public, and
the new catch-all proxy must not expose them); `error_page 502 503 504 =503 @shell` serves the
static `index.html` with 503 and `no-store` when the backend is down.
`proxy_intercept_errors` only rewrites 502–504, so the backend's own 404 pages pass through.
`qbits-proxy.conf` (in `/etc/nginx/snippets/`) sets `Host`, `X-Real-IP`, `X-Forwarded-For`.
Cloudflare terminates TLS; "Always Use HTTPS" in Cloudflare handles http → https (operator step).

`deploy-app.sh` copies `nginx-qbits.conf` and `qbits-proxy.conf`, runs `sudo nginx -t`, and
restores the previous config and stops if the test fails. It adds `QBITS_SHELL_FILE` to the
instance's `.env` if missing, and after the restart checks that `/` comes from the backend and
loads the bundle, `robots.txt` is served, and an unknown path answers 404. `cloud-init.yaml`
keeps its first-boot config; the first deploy replaces it. Order is unchanged: `dist` is copied
before the backend restarts, so the new shell is read.

### OpenAPI
`springdoc.paths-to-match: /api/**` keeps `robots.txt` and the sitemaps (a `@RestController`) out
of the API contract.

## Error handling
- All page-route failures render HTML, never Problem Details JSON. `/api/**` keeps `ProblemAdvice`.
- Logs: `path=/story/… status=404 durationMs=…` at DEBUG; 5xx at ERROR. No story text logged.

## Testing approach
| Requirement | Test |
|---|---|
| R1.6, R2.1–R2.4, R2.7, R2.8 | `PageRendererTest`: shell replacement, tag insertion, escaping of `<script>`, quotes and `&` in headlines |
| R2.7 | `HtmlTest`: text, attribute, JSON-in-script escaping incl. `</script>` and U+2028 |
| R1.1–R1.4, R1.7, R2.2 | `PageContentTest`: required fields present, no excerpt fields, description cut at word |
| R2.5, R2.6 | `JsonLdTest` |
| R6.1–R6.4 | `SitemapsTest`: XML parses, URLs escaped, 48 h cut-off with fixed `Clock` |
| R1.1–R1.5, R3, R4.1, R7 | `PageControllerIT` (Testcontainers): seeded stories → status, title, headline, initial-data JSON equals `/api/v1` response body, cache headers, unknown paths 404, `/api/v1/*` still JSON |
| R6, R7.2 | `CrawlControllerIT` |
| R4.2–R4.4 | `initial-data.test.ts`: seeds matching path; infinite shape; fresh timestamp; ignores other path, bad JSON, wrong shape |
| R4.2 | `StoryPage.test.tsx`, `LatestPage.test.tsx` additions: with seeded cache, no skeleton on first render and no request (MSW unhandled-request check); Playwright `seo.spec.ts` with the real bundle |
| R2.1, R5.1 | `page-titles.test.ts` and `PageTitlesTest.java` both check `specs/007-seo-pages/titles.json`; page tests and `seo.spec.ts` assert `document.title` |
| R8.1, R8.4 | nginx config run in Docker against a stub backend (routing, 404 passthrough, www 301, backend-down shell); `deploy-app.sh` post-restart checks on the live instance |

## Alternatives considered
- **Build-time pre-render on the developer machine** — stories are written on the server after the
  build, so pages would be empty until the next deploy.
- **Node SSR (Next.js / React Router framework / Vite SSR)** — needs Node on a 1 GB instance next
  to the JVM and Postgres, a third service, and a front-end rework.
- **Headless Chrome / prerender service** — memory or third-party cost; bot-only HTML is cloaking-adjacent.
- **Writing static HTML files at edition publish** — faster, but stale/deleted-story file
  management; per-request rendering with caching is simpler and always correct.
- **Thymeleaf templates** — a second copy of the layout; the Vite shell already carries the
  hashed asset names, so filling it is less to keep in sync.

## Risks
- Server markup and React markup drift apart visually → a brief layout shift. Mitigated by reusing
  class names and seeding data; acceptable because it is not hydration and cannot break the app.
- Title strings duplicated in Java and TypeScript → shared fixture test.
- A future route added in React but not in `PageController` returns 404 to crawlers → the web
  route list and `PageController` mappings are checked against each other in `PageControllerIT`
  (fixture list of app routes).
- Making the site indexable is a public release (see requirements Open questions).
