# 005 — Open-source links: Requirements

## Summary
When a story is about something open, such as public code, model weights, a dataset or a research paper, show up to three direct links next to it: "Code on GitHub", "Model on Hugging Face", "Paper on arXiv". Links come **only** from URLs the publisher put in its feed entry, on an allowlist of hosts, and each is confirmed to exist through the host's official API before anyone sees it. Q-Bits never guesses, generates or searches for a link.

## In scope
- Pulling candidate links out of feed entries at ingestion (URLs only)
- Recognising and normalising GitHub, GitLab, Hugging Face and arXiv links
- Checking each link through the host's API, with caching and rate limits
- Returning verified links in the API and showing them on story and feed cards

## Out of scope
- Searching GitHub or Hugging Face for a project named in a story (risk of wrong matches)
- Generating links with the summary service
- Showing repo stars, licences, README text or any other content from the hosts
- New sources (see open questions)

## Behaviours and acceptance criteria

### R1 — Finding candidate links
- **R1.1** WHEN a feed entry is ingested THE SYSTEM SHALL collect candidate URLs from: the entry's own links, `href` targets in its description, plain-text URLs in its description, and `href` targets in its content element.
- **R1.2** THE SYSTEM SHALL keep only the URLs. Description and content text used to find them SHALL NOT be stored, logged or sent anywhere.
- **R1.3** Candidates SHALL be stored against the item with status `PENDING` and their origin (`ENTRY_LINK`, `DESCRIPTION`, `CONTENT`).

### R2 — Allowlist and normal forms
- **R2.1** Only these hosts are accepted: `github.com`, `gitlab.com`, `huggingface.co`, `arxiv.org` (including `www.` and `export.` variants). Anything else is dropped.
- **R2.2** URLs SHALL be normalised to the forms in `steering/conventions.md`:
  - GitHub/GitLab: `https://github.com/{owner}/{repo}` from any deeper path (`/tree/…`, `/blob/…`, `/releases/…`); type `CODE`.
  - Hugging Face: models `https://huggingface.co/{org}/{name}` → `MODEL`; `/datasets/{org}/{name}` → `DATASET`; `/spaces/{org}/{name}` → `CODE`; `/papers/{arxivId}` → `PAPER` at `https://arxiv.org/abs/{arxivId}`.
  - arXiv: `/abs/{id}`, `/pdf/{id}`, `/html/{id}` with or without a version → `https://arxiv.org/abs/{id}` without the version; type `PAPER`.
- **R2.3** THE SYSTEM SHALL drop non-project pages: user or organisation roots (`github.com/openai`), site pages (`github.com/features`, `/topics/…`, `/sponsors/…`, `/login`, gists), Hugging Face non-repo pages (`/blog`, `/docs`, `/pricing`, `/models` search), arXiv listing pages.
- **R2.4** Duplicates of the same normalised URL in one entry SHALL be stored once.

### R3 — Relevance guard
- **R3.1** A candidate from `ENTRY_LINK` or `DESCRIPTION` SHALL be kept.
- **R3.2** A candidate from `CONTENT` SHALL be kept only if a word of 3+ letters from its owner or project name (split on `-`, `_`, `.`) appears in the source title or excerpt, case-insensitively. This stops share buttons, footers and "follow us" links from appearing.

### R4 — Checking links
- **R4.1** A background job SHALL check `PENDING` candidates through the host's official API: GitHub REST `GET /repos/{owner}/{repo}`, GitLab `GET /api/v4/projects/{path}`, Hugging Face Hub `GET /api/{models|datasets|spaces}/{id}`, arXiv export API `query?id_list={id}`.
- **R4.2** A 200 response (for arXiv, an entry with no error) SHALL mark the link `VERIFIED`. When GitHub reports a renamed repo, THE SYSTEM SHALL store the new canonical URL.
- **R4.3** A 404 (or arXiv "not found") SHALL mark it `NOT_FOUND`. It is never shown or checked again.
- **R4.4** Errors, 403/429 rate limits and timeouts SHALL mark it `CHECK_FAILED` and retry up to 3 times with growing delays (10 min, 1 h, 6 h), then give up as `NOT_FOUND`.
- **R4.5** Results SHALL be cached by normalised URL for 24 hours, so the same repo linked from several stories is checked once.
- **R4.6** Calls SHALL stay within each host's limits: GitHub at most 50 calls an hour without `GITHUB_TOKEN` (configurable higher with a token), arXiv at least 3 seconds apart, and the same User-Agent as feed fetching.
- **R4.7** Nothing from the host's response SHALL be stored except the status and, for GitHub, the canonical `owner/repo`.

### R5 — In the API
- **R5.1** Every story in `/api/v1/stories` and `/api/v1/stories/{id}` SHALL include `resources`: up to 3 `VERIFIED` links, each `{ type, label, url, name }`, ordered code, model, dataset, paper. Empty list when none.
- **R5.2** `label` SHALL be a fixed string from type and host ("Code on GitHub", "Code on GitLab", "Model on Hugging Face", "Dataset on Hugging Face", "Demo on Hugging Face" for spaces, "Paper on arXiv"). `name` is the path such as `openai/whisper` or `2410.01234`.

### R6 — On cards
- **R6.1** Story cards (003) and feed cards (004) SHALL show an "Open source" row with each resource as a link: label plus the `name` in a monospace style.
- **R6.2** Each link SHALL open in a new tab with `rel="noopener noreferrer"` and an accessible name like "Code on GitHub: openai/whisper (opens in a new tab)".
- **R6.3** The row SHALL not appear when `resources` is empty. On feed cards it is visible without expanding.
- **R6.4** Links may appear after the story does, once checks finish; the card simply shows them on the next load.

## Non-functional
- Ingestion speed for a full run stays within 001's 2-minute target; link checks run separately.
- No test calls a real host API; WireMock stands in for all four.
- A story never waits for link checks to be published.

## Principles touched
Content and legal (open-source links from the feed only, URLs only, checked via official APIs, never guessed), Fetching (official APIs within limits, honest User-Agent), Architecture (checking lives in the backend).

## Open questions
- Should we add sources that often link to open-source work, for example the GitHub Blog's AI feed or the Hugging Face Papers feed? Each would need the usual terms review.
- Do you want an "Open source" filter on AI Latest later (show only stories with code or models)?
