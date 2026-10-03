# Principles

Flat rules. If a spec or a task conflicts with one of these, stop and flag it; do not work around it.

## Content and legal
- The app links out. It never copies, hosts or republishes articles.
- Stored per story: source title, source excerpt, canonical URL, source id, published_at, fetched_at, section, relevance result, our headline, our summary, key terms. Nothing else from the article.
- Source excerpts come only from the feed's own description/summary, stripped to plain text, max 300 characters. They are inputs for writing the summary and are not shown to readers.
- Summaries, headlines, sections and key terms are generated from the source title and excerpt only. The app never fetches article pages.
- Summaries are in our own words: no copied sentences or distinctive phrases, no quotations, 120 words maximum, and no facts beyond the input.
- Every story shows its source name, publish time, a "Read the full story" link to the original URL, and a note that the summary was written from the publisher's headline and teaser.
- No article bodies, full-text extraction, reader views, iframes or in-app web views. Ever. On mobile, links open in the system browser.
- No downloaded or hot-linked images. No publisher logos. Source names are plain text.
- Only stories published in the last 72 hours are shown. Stored stories are deleted after 7 days.
- Takedown: a source can be disabled in config, which hides its stories at once. A purge removes them within 24 hours.
- Only the source title and excerpt are sent to the summary service. No reader data is ever sent.

## Fetching
- Fetch only sources listed in `config/sources.yml`. Each entry records access method, terms URL and the date its terms were last reviewed.
- Access methods are RSS, Atom or a documented public API. No HTML page scraping.
- Check robots.txt before fetching and honour Disallow and Crawl-delay.
- Send an honest User-Agent naming the app and a contact address.
- Use conditional GET (ETag / If-Modified-Since). Default interval 30 minutes per source, never below 15.
- Never bypass paywalls, logins, rate limits or bot checks.

## Architecture
- One deployable backend (modular monolith) until a measured need says otherwise.
- Clients (web, mobile) hold no business rules. Filtering, sectioning, summarising and the 72-hour window live in the backend; clients only display.
- Summaries are written once, in the background, and stored. Readers never trigger a call to the summary service.
- A story is shown only after its summary passes validation. If the summary service is down, ingestion carries on and summaries catch up later.
- The versioned REST API is the only way clients reach data.
- PostgreSQL is the only state. App instances are stateless and safe to run twice.
- Ingestion is idempotent. Canonical URL is unique; re-fetching never creates duplicates.
- One failing source never blocks or fails other sources.
- Pure domain logic has no framework dependencies and no I/O.
- Secrets come from environment variables only, never from committed files.
- No personal data in v1. Accounts, if added, collect the minimum needed and follow India's DPDP Act.

## Process
- The approved spec is the source of truth. If code and spec disagree, change the spec first, get it re-approved, then change the code.
- Implementation starts only when the feature's `.status` reads `tasks:approved`.
- Only a human writes `:approved` or `done` into a `.status` file.
- Every acceptance criterion has a test. A criterion with no test is not done.
