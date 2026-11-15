# 007 — Search-ready pages: Requirements

## Summary
qbitsnews.com is live on a public domain, but every URL returns the same empty React shell
(`<div id="root"></div>`, title "Q-Bits"). Search engines and link previews see no headline, no
summary and no per-page title, `/robots.txt` and `/sitemap.xml` return the home page, and unknown
URLs return 200. This feature makes the backend fill in the first HTML response of each page with
its own metadata and readable content, adds real `robots.txt` and sitemaps, and returns real 404s.
The web app stays a single-page app: after the first load, navigation and data fetching are
unchanged.

## In scope
- Server-filled first response for `/`, `/sections`, `/section/{slug}`, `/story/{id}`, `/about`
- Per-page title, description, canonical URL, Open Graph and Twitter tags, JSON-LD
- Initial data embedded in the page so the web app renders without a loading flash
- `robots.txt`, `sitemap.xml`, Google News sitemap
- Real 404 status for unknown routes, unknown sections and missing or expired stories
- Page titles that also update during in-app navigation
- nginx routing for the above, shipped by the deploy script

## Out of scope
- A story archive beyond the 72-hour window (conflicts with 7-day retention, see Open questions)
- Full server-side React rendering or hydration
- Images, `og:image` or logos (principles)
- Analytics, Search Console setup, Cloudflare settings (operator tasks, listed in design)
- Changes to `/api/v1` responses

## Behaviours and acceptance criteria

### R1 — Server-filled pages
As a search engine or link preview, I want each URL's first response to contain its own content,
so that I can index and describe it without running JavaScript.

- **R1.1** WHEN `/story/{id}` is requested for a story the API would return THE SYSTEM SHALL
  respond 200 with HTML containing the story's headline in an `<h1>`, its summary, its key terms,
  its source name, the "Read the full story at <Source>" link to the original URL, and the
  attribution line.
- **R1.2** WHEN `/section/{slug}` is requested for a known section THE SYSTEM SHALL respond 200
  with the section name in an `<h1>`, its description, and the first page of its stories as
  headlines linking to `/story/{id}`.
- **R1.3** WHEN `/` is requested THE SYSTEM SHALL respond 200 with "AI Latest" in an `<h1>` and
  the first page of the latest edition's stories (headline, summary, source, link to the story).
- **R1.4** WHEN `/sections` is requested THE SYSTEM SHALL respond 200 with each section's name,
  description and its newest five stories as links.
- **R1.5** WHEN `/about` is requested THE SYSTEM SHALL respond 200 with the page metadata (R2) and
  an empty app root.
- **R1.6** Every filled page SHALL load the same JS and CSS bundle as the current `index.html`.
- **R1.7** Filled content SHALL contain only fields the API already returns; it SHALL NOT include
  the source title or source excerpt.

### R2 — Page metadata
- **R2.1** Each page in R1 SHALL have a unique `<title>`: story "<headline> | Q-Bits"; section
  "<section name>: AI news from the last 72 hours | Q-Bits"; home "Q-Bits: today's AI news,
  explained in plain words"; overview "AI news by section | Q-Bits"; about "About Q-Bits".
- **R2.2** Each page SHALL have a `<meta name="description">`: a story's summary cut to at most
  160 characters at a word boundary; a section's description; fixed text for the other pages.
- **R2.3** Each page SHALL have `<link rel="canonical">` with the absolute URL on the configured
  site origin, without query string.
- **R2.4** Each page SHALL have `og:title`, `og:description`, `og:url`, `og:site_name`, `og:type`
  (`article` for stories, `website` otherwise) and `twitter:card` = `summary`. No `og:image`.
- **R2.5** A story page SHALL include JSON-LD `NewsArticle` with `headline`, `description`,
  `datePublished`, `url`, `isBasedOn` (the original URL), `articleSection`, and `publisher` and
  `author` as the Organization "Q-Bits".
- **R2.6** The home page SHALL include JSON-LD `WebSite` and `Organization`.
- **R2.7** All inserted text SHALL be HTML-escaped, and JSON inside `<script>` SHALL escape `<`, so
  no story text can break out of its element.
- **R2.8** `/login` and `/register` SHALL respond 200 with the shell and
  `<meta name="robots" content="noindex">`.

### R3 — Not found
- **R3.1** WHEN `/story/{id}` is requested for a missing, hidden or expired story, or a malformed
  id, THE SYSTEM SHALL respond 404 with the shell, title "Story not available | Q-Bits" and
  `noindex`.
- **R3.2** WHEN `/section/{slug}` is requested for an unknown slug THE SYSTEM SHALL respond 404 with
  the shell and `noindex`.
- **R3.3** WHEN any other path that is not a static file is requested THE SYSTEM SHALL respond 404
  with the shell and `noindex`. The web app still shows its own not-found page.

### R4 — No loading flash
As a reader opening a link, I want the page to show its content at once, not a skeleton.

- **R4.1** Each filled page SHALL embed the data it was built from in
  `<script id="qbits-initial-data" type="application/json">`, in the same shape as the matching
  `/api/v1` response.
- **R4.2** WHEN the web app starts and the initial data matches the current route THE SYSTEM SHALL
  seed the query cache with it, so the first render shows content without a request or skeleton.
- **R4.3** IF the initial data is missing or unreadable THEN the web app SHALL behave exactly as
  today.
- **R4.4** Seeded data SHALL be treated as fresh for the normal stale time (60 s), then refetched
  as usual.

### R5 — Titles during navigation
- **R5.1** WHEN the reader navigates inside the app THE SYSTEM SHALL set `document.title` to the
  same title R2.1 defines for that page.

### R6 — Crawling files
- **R6.1** `GET /robots.txt` SHALL return `text/plain` allowing all crawlers, disallowing `/api/`,
  `/login` and `/register`, and naming both sitemaps by absolute URL.
- **R6.2** `GET /sitemap.xml` SHALL list `/`, `/sections`, `/about`, every section page, and every
  story the stories API currently returns, with `lastmod`.
- **R6.3** `GET /sitemap-news.xml` SHALL list stories published in the last 48 hours in Google News
  sitemap format, with publication name "Q-Bits" and language `en`.
- **R6.4** Both sitemaps SHALL be valid XML with escaped URLs.

### R7 — Caching
- **R7.1** Filled pages SHALL carry `Cache-Control: public, max-age=300`; 404 pages
  `Cache-Control: no-store`.
- **R7.2** Sitemaps and `robots.txt` SHALL carry `Cache-Control: public, max-age=3600`.

### R8 — Deployment
- **R8.1** nginx SHALL send `/`, `/sections`, `/section/*`, `/story/*`, `/about`, `/login`,
  `/register`, `/robots.txt`, `/sitemap.xml`, `/sitemap-news.xml` and any path that is not a file
  in `web/dist` to the backend; files in `web/dist` (assets, `favicon.svg`) stay served by nginx.
- **R8.2** `deploy-app.sh` SHALL install the nginx config on every deploy, test it with
  `nginx -t`, and reload nginx.
- **R8.3** nginx SHALL redirect `www.qbitsnews.com` to `qbitsnews.com` with 301.
- **R8.4** IF the backend is down THEN nginx SHALL serve the plain `index.html` so the app still
  loads (with an error state from the API).

## Non-functional
- A filled page reuses `CatalogService` and `EditionQueries`: no extra queries beyond the API calls
  it mirrors, and no calls to external services.
- p95 server time for a filled page under 100 ms on the t3.micro with a warm database.
- No new runtime or process on the instance; no Node on the server.
- Works with the current local build and `deploy-app.sh` flow.

## Principles touched
- "The app links out. It never copies, hosts or republishes articles." Pages show only our
  headline, summary and key terms, plus the outbound link (R1.7).
- "Source excerpts … are not shown to readers." Never inserted (R1.7).
- "No downloaded or hot-linked images. No publisher logos." No `og:image` (R2.4).
- "Every story shows its source name, publish time, a 'Read the full story' link … and a note that
  the summary was written from the publisher's headline and teaser." Included in R1.1.
- "Clients … hold no business rules." Windows and visibility still come from `CatalogService`.
- "The versioned REST API is the only way clients reach data." Unchanged: pages are not a client
  API, and the web app keeps using `/api/v1` after the first load.
- Conventions "The API … returns data, never HTML." Respected: HTML is served outside `/api`.

## Decisions (2026-10-05, owner)
- **Public release:** yes, the site is a public release. Pages are indexable; no site-wide
  `noindex`.
- **Expired stories:** 404, as drafted. No archive in this spec.
- **`datePublished`:** the source's publish time (`publishedAt`).

## Open questions
- **tech-stack.md** says "server rendering and SEO are not goals … revisit if it goes public." This
  spec is that revisit; `product.md` ("first user … public release is a later decision") also
  needs updating. Covered by T5.
- **Cloudflare HTML caching.** Pages could be cached at the edge with a purge on edition publish.
  Out of scope for now; 300 s browser cache only.
