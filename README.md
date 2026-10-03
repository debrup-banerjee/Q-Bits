# Q-Bits

AI news from the last 72 hours, in plain words, with links to every original story.

Start with `CLAUDE.md` and `steering/product.md`. Specs live in `specs/`.

## Run it locally

Needs Java 21+, Maven, Node 22+ and Docker (for PostgreSQL).

```bash
docker compose up -d                       # PostgreSQL 16 on localhost:5432
export ANTHROPIC_API_KEY=...               # summaries stay pending without it
export QBITS_STORY_MODEL=...               # the model to write summaries with
export GITHUB_TOKEN=...                    # optional: raises GitHub's limit for open-source link checks
mvn -f backend/pom.xml spring-boot:run     # API and background jobs on :8080
npm install && npm run dev --workspace web # web app on http://localhost:5173
```

The backend checks open-source links through `api.github.com`, `gitlab.com`, `huggingface.co`
and `export.arxiv.org`, so those hosts must be reachable when it runs (tests never call them).

The backend refuses to start until every enabled source in `config/sources.yml` has
`termsUrl` and `termsReviewedOn` filled in (spec 001, R1.4).

## Checks

```bash
mvn -f backend/pom.xml verify              # backend unit + integration tests, formatting
npm run verify                             # API client and web app: types, lint, tests
npm run e2e --workspace web                # browser tests at phone and desktop widths
```

Integration tests start PostgreSQL with Testcontainers (needs Docker). Without Docker, point
them at a database with `QBITS_TEST_DB_URL`, `QBITS_TEST_DB_USER` and `QBITS_TEST_DB_PASSWORD`.

After changing the API, update the contract and regenerate the client:

```bash
mvn -f backend/pom.xml verify -Dqbits.updateOpenApi=true
npm run generate:api
```
