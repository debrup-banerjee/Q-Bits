# Tech stack

Each choice lists why it was made and what was considered instead. Pin exact versions in `pom.xml`, not here.

| Area | Choice | Why | Not chosen |
|---|---|---|---|
| Language | Java 21 (LTS); move to Java 25 LTS once the build environment has it | Owner's strongest ecosystem; virtual threads make many slow feed fetches cheap without reactive code. Java 21 is what the build environment provides today. | Kotlin — fine, but one language keeps reviews simple. |
| Framework | Spring Boot 4.x | Scheduling, config binding, HTTP client, validation and Actuator in one place. | Quarkus/Micronaut — faster startup we don't need. |
| Build | Maven | Predictable, widely understood, good IDE support. | Gradle — more flexible than this project needs. |
| Database | PostgreSQL 16 | Unique constraints for de-duplication, full-text search later, JSONB if needed. | SQLite — weak for concurrent writer + reader; MongoDB — data is relational. |
| Migrations | Flyway | Plain SQL, versioned, runs on startup. | Liquibase — XML/YAML overhead. |
| Data access | Spring Data JDBC | Small aggregates, explicit SQL, no lazy-loading surprises. | JPA/Hibernate — more magic than a feed store needs. |
| Feed parsing | Rome (`com.rometools`) | Mature RSS/Atom parser that handles real-world malformed feeds. | Hand-rolled XML parsing. |
| HTTP | Spring `RestClient` on JDK `HttpClient` | Conditional GET (ETag / If-Modified-Since), timeouts, honest User-Agent. | WebClient — reactive stack not needed. |
| robots.txt | crawler-commons | Correct, well-tested robots.txt parsing incl. Crawl-delay. | Ad-hoc parsing. |
| HTML cleanup | Jsoup | Strips markup from feed excerpts safely. | Regex. |
| Scheduling | `@Scheduled` + ShedLock | Simple in-process jobs; ShedLock prevents double runs if two instances start. | Quartz — heavier; external queue — not needed yet. |
| Relevance pre-filter | Rules-based keyword scoring | Cheap first pass on general-news feeds so only likely AI stories reach the summary service. | Sending every item to the model — cost with no benefit. |
| Summary, section and key terms | Anthropic Messages API via the official Java SDK (`com.anthropic:anthropic-java`), behind a `StoryWriter` interface | One structured call per story returns relevance, section, plain headline, summary and key terms as JSON. Strong at plain, accurate writing. The interface keeps the provider swappable and lets tests use a fake. Model name and API key come from config/env. | OpenAI — viable, same interface; on-device/open models — weaker plain-language writing, more ops work. |
| Output validation | Jackson + Bean Validation on the returned JSON, plus a copy-check (n-gram overlap with the excerpt) | Rejects summaries that are too long, copy the source, or miss required fields before anything is shown. | Trusting model output as-is. |
| API contract | springdoc-openapi | The OpenAPI document generated from the controllers is the single contract for the web app now and the mobile app later. | Hand-written API docs — drift. |
| Tests | JUnit 5, AssertJ, Testcontainers, WireMock | Real Postgres in tests; feeds stubbed, so tests never touch the network. | H2 — behaves differently from Postgres. |
| Formatting | Spotless + google-java-format | No style debates in review. | Checkstyle-only. |
| Local run | Docker Compose (Postgres) | One command to start dependencies. | Installing Postgres locally. |
| Observability | Actuator + Micrometer, structured logs | Per-source fetch health is the main thing to watch. | Full tracing stack — later if needed. |

## Front end

Chosen because the product needs a responsive web app now and a mobile app later, sharing as much code as possible.

| Area | Choice | Why | Not chosen |
|---|---|---|---|
| Language | TypeScript (strict) | Types generated from the API catch contract breaks at build time, in web and mobile alike. | Plain JavaScript. |
| Web UI | React + Vite (static SPA) | Responsive web from one codebase; skills and patterns carry over to React Native. | Next.js — server rendering and SEO are not goals for a team reader; revisit if it goes public. Thymeleaf + HTMX — nothing reusable for mobile. |
| Routing | React Router | Standard, small. | — |
| Server state | TanStack Query | Caching, background refresh and pagination for the item list; also works in React Native. | Redux — no client-side state complex enough to need it. |
| Styling | Tailwind CSS, mobile-first | Responsive layouts by default, no stylesheet sprawl. | CSS-in-JS — runtime cost, no gain here. |
| API client | Generated from OpenAPI (`openapi-typescript` + `openapi-fetch`) in `packages/api-client` | One typed client shared by web and mobile; regenerated when the backend contract changes. | Hand-written fetch calls. |
| Mobile (later) | React Native with Expo | Reuses TypeScript, the API client and query hooks; one team, one language on the client side. | Native Swift/Kotlin — two more codebases; Flutter — no code shared with web. |
| Tests | Vitest + React Testing Library, MSW for API mocks, Playwright for end-to-end | Fast unit tests; MSW keeps tests off the real backend; Playwright checks phone and desktop widths. | Jest — slower with Vite; Cypress — fine, Playwright covers more browsers. |
| Lint/format | ESLint + Prettier | No style debates in review. | — |
| Packages | npm workspaces | Enough for `web`, `mobile` and `packages/*` without extra tooling. | Nx/Turborepo — add only if builds get slow. |

## Shape

```
backend/               Spring Boot app: ingestion jobs + REST API (modular monolith)
web/                   React SPA, built to static files
mobile/                React Native (Expo) app — later
packages/api-client/   Generated TypeScript client + shared types and query hooks
```

The backend is one deployable app: ingestion runs as scheduled jobs and the REST API reads from the same database. Clients talk to it only through `/api/v1`. The web app is served as static files (CDN or any static host). Split the backend only when a measured need appears.
