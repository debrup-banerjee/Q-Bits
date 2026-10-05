# 007 — Search-ready pages: Tasks

Each task is 2–4 hours, leaves the full check green, and can be tested on its own.

## T1 — Pure rendering pieces
- **Covers:** R1.6, R1.7, R2.1–R2.8, R6.1–R6.4
- **Depends on:** none
- **Do:** `com.qbits.pages.domain`: `Html`, `PageModel`, `PageRenderer`, `PageContent`, `JsonLd`,
  `Sitemaps`. No Spring. Add `specs/007-seo-pages/titles.json` (page kind → expected title).
- **Tests:** `HtmlTest`, `PageRendererTest`, `PageContentTest`, `JsonLdTest`, `SitemapsTest`,
  `PageTitlesTest`.
- **Done when:** `mvn -f backend/pom.xml verify` passes.

## T2 — Page and crawl controllers
- **Covers:** R1.1–R1.5, R2.8, R3.1–R3.3, R4.1, R6, R7
- **Depends on:** T1
- **Do:** `PagesProperties`, `PageShell`, `PageController`, `CrawlController`; config keys in
  `application.yml` and `deploy/aws/env.example`; catch-all mapping that leaves `/api/**`,
  `/v3/**`, `/actuator/**` untouched.
- **Tests:** `PageControllerIT` (incl. initial data equals the API body; route list fixture),
  `CrawlControllerIT`; existing API ITs unchanged.
- **Done when:** `curl localhost:8080/story/<id>` shows the headline in the HTML; unknown paths 404.

## T3 — Web: seed from initial data, page titles
- **Covers:** R4.2–R4.4, R5.1, R2.1
- **Depends on:** T2 (shape only; can be built against fixtures)
- **Do:** `web/src/app/initial-data.ts` (`seedInitialData`) called from `main.tsx` with the app's query client
  (`Providers` takes it as a prop already); `page-titles.ts`; `usePageTitle` in each page component.
- **Tests:** `initial-data.test.ts`, `page-titles.test.ts`, `StoryPage.test.tsx` and `LatestPage.test.tsx`
  additions (no skeleton with seeded data; `document.title`), Playwright `seo.spec.ts` checking
  titles on navigation.
- **Done when:** `npm run verify --workspace web` and `npm run e2e --workspace web` pass.

## T4 — nginx and deploy
- **Covers:** R8.1–R8.4
- **Depends on:** T2
- **Do:** `deploy/aws/nginx-qbits.conf`, `deploy/aws/qbits-proxy.conf`; `deploy-app.sh` copies
  them, runs `sudo nginx -t`, reloads; `cloud-init.yaml` uses the same files for new instances;
  README deploy notes and the operator checklist (Search Console, sitemap submit, Cloudflare
  "Always Use HTTPS", www DNS record).
- **Tests:** `nginx -t` in the deploy run. Manual after deploy: `curl -I` for `/`, `/story/<id>`,
  `/nope` (404), `/robots.txt`, `/sitemap.xml`, `/assets/<file>`, `www.` (301); stop the backend
  and confirm `/` still serves the shell.
- **Done when:** all checks above pass on qbitsnews.com.

## T5 — Steering updates
- **Covers:** Open questions follow-up
- **Depends on:** approval of this spec
- **Do:** Update `steering/product.md` ("Who uses it": public release), `steering/tech-stack.md` front-end row (server-filled pages instead of "SEO not a
  goal"), and `conventions.md` (page routes live in `com.qbits.pages`, HTML only outside `/api`).
- **Tests:** none (docs).
- **Done when:** steering matches what was built.
