# 005 — Open-source links: Design

## Overview
At ingestion, `FeedParser` collects every URL it can see in an entry (entry links, description anchors and plain URLs, content anchors) and passes them on as a list of `(url, origin)` pairs; the content text itself is never kept. A pure `ResourceLinkNormaliser` turns allowlisted URLs into typed, canonical candidates and drops everything else; `ResourceRelevance` applies the name-match guard to content links. Candidates are saved as `PENDING`. A separate `ResourceCheckJob` verifies them through each host's API (`HostApis`), sharing a 24-hour cache. The catalog read model joins verified resources onto each story.

```
feed entry ──▶ FeedParser (URLs + origin) ──▶ ResourceLinkNormaliser ──▶ ResourceRelevance ──▶ item_resources (PENDING)
                                                                                                   │
                         ResourceCheckJob (every 5 min, lock) ◀─────────────────────────────────────┘
                              │  link_check_cache (24 h)
                              ▼
                         HostApis: GitHub · GitLab · Hugging Face · arXiv  (rate-limited, honest UA)
                              │
                              ▼
                      VERIFIED / NOT_FOUND / CHECK_FAILED ──▶ /api/v1/stories … "resources": [...]
```

## Components
| Component | Package | Responsibility | Requirements |
|---|---|---|---|
| `RawEntry.links` | `ingestion.domain` | New field: `List<FoundLink(url, origin)>`. | R1.1 |
| `FeedParser` | `ingestion` | Collect links with Jsoup from description and content HTML (anchors only) and a URL regex on description text; discard the text. | R1.1, R1.2 |
| `ResourceLinkNormaliser` | `resources.domain` | Pure: host allowlist, path rules, canonical URL, type, `name`. Returns `Optional<Candidate>`. | R2 |
| `ResourceRelevance` | `resources.domain` | Pure: name-word match for `CONTENT` origin. | R3 |
| `ItemResourceRepository` | `resources.persistence` | Insert candidates (unique per item and URL), find due checks, update status, find verified per item. | R1.3, R4, R5 |
| `LinkCheckCacheRepository` | `resources.persistence` | 24-hour cache by URL. | R4.5 |
| `HostApis` | `resources` | One method per host, using the feed `RestClient`; maps status codes to `Found(canonicalName)`, `Missing`, `Failed`. Per-host rate limiter. | R4.1–R4.3, R4.6, R4.7 |
| `ResourceCheckJob` | `resources` | Scheduled with ShedLock; picks due candidates, uses cache, calls `HostApis`, applies retry schedule. | R4 |
| `SourceIngestor` | `ingestion` | After inserting a new item, normalise and store its candidates. | R1.3 |
| `StoryView.resources` + query | `catalog` | Join verified resources, order, limit 3, build labels. | R5 |
| `ResourceLinks` | `web/src/components` | The "Open source" row. | R6 |

## Data model (migration `V4__item_resources.sql`)
```sql
create table item_resources (
  id            bigserial primary key,
  item_id       uuid not null references items(id) on delete cascade,
  type          text not null check (type in ('CODE','MODEL','DATASET','PAPER')),
  host          text not null check (host in ('github','gitlab','huggingface','arxiv')),
  url           text not null check (char_length(url) <= 300),
  name          text not null check (char_length(name) <= 200),
  origin        text not null check (origin in ('ENTRY_LINK','DESCRIPTION','CONTENT')),
  status        text not null default 'PENDING'
                  check (status in ('PENDING','VERIFIED','NOT_FOUND','CHECK_FAILED')),
  attempts      int not null default 0,
  next_check_at timestamptz not null,
  checked_at    timestamptz,
  unique (item_id, url)
);
create index item_resources_due_idx on item_resources (status, next_check_at);

create table link_check_cache (
  url        text primary key,
  status     text not null check (status in ('VERIFIED','NOT_FOUND')),
  canonical  text,
  checked_at timestamptz not null
);
```
Only URLs, names and statuses are stored. No text from feeds or hosts.

## Normalisation rules (pure, table-tested)
| Input | Output |
|---|---|
| `https://github.com/openai/whisper/tree/main/src` | CODE, github, `https://github.com/openai/whisper`, `openai/whisper` |
| `http://www.github.com/OpenAI/Whisper.git` | CODE, `https://github.com/OpenAI/Whisper` (case kept; GitHub resolves) |
| `https://github.com/openai` · `/features/copilot` · `/topics/llm` · `gist.github.com/…` | dropped |
| `https://huggingface.co/meta-llama/Llama-4-8B/blob/main/config.json` | MODEL, `https://huggingface.co/meta-llama/Llama-4-8B` |
| `https://huggingface.co/datasets/allenai/c4` | DATASET |
| `https://huggingface.co/spaces/org/demo` | CODE (label "Demo on Hugging Face") |
| `https://huggingface.co/papers/2410.01234` | PAPER, `https://arxiv.org/abs/2410.01234` |
| `https://huggingface.co/blog/…` · `/docs/…` · `/models?search=…` | dropped |
| `https://arxiv.org/pdf/2410.01234v2` · `/html/2410.01234v1` | PAPER, `https://arxiv.org/abs/2410.01234` |
| `https://arxiv.org/list/cs.AI/recent` | dropped |
| any other host | dropped |

Reserved GitHub first segments dropped: `features, topics, sponsors, login, join, marketplace, orgs, settings, about, pricing, collections, trending, explore, apps, enterprise, security, site, events, search, notifications, new, codespaces, customer-stories, readme`.

## Host API calls
| Host | Request | Found | Missing | Notes |
|---|---|---|---|---|
| GitHub | `GET https://api.github.com/repos/{owner}/{repo}` (`Accept: application/vnd.github+json`, optional `Authorization: Bearer $GITHUB_TOKEN`) | 200 → `full_name` | 404 | Redirects for renamed repos are followed; 403/429 → Failed. |
| GitLab | `GET https://gitlab.com/api/v4/projects/{urlencoded path}` | 200 | 404 | |
| Hugging Face | `GET https://huggingface.co/api/{models|datasets|spaces}/{id}` | 200 | 404, 401 (gated or private, treated as missing) | |
| arXiv | `GET https://export.arxiv.org/api/query?id_list={id}` | Atom entry whose `<id>` ends with the id | entry with "Error" title | ≥3 s between calls. |

Per-host limiter: a token bucket configured in `qbits.resources.rate-limits` (github 50/h, gitlab 100/h, huggingface 100/h, arxiv 1 per 3 s). When a bucket is empty, the job leaves the rest for its next run.

**Config**
```yaml
qbits:
  resources:
    tick: PT5M
    batch-size: 30
    max-per-story: 3
    cache-ttl: PT24H
    retry-delays: [PT10M, PT1H, PT6H]
    github-token: ${GITHUB_TOKEN:}
    base-urls:            # overridable in tests
      github: https://api.github.com
      gitlab: https://gitlab.com
      huggingface: https://huggingface.co
      arxiv: https://export.arxiv.org
```

## API contract
`StoryView` gains:
```json
"resources": [
  { "type": "code",  "label": "Code on GitHub",        "url": "https://github.com/openai/whisper", "name": "openai/whisper" },
  { "type": "paper", "label": "Paper on arXiv",        "url": "https://arxiv.org/abs/2410.01234",  "name": "2410.01234" }
]
```

## Flow
1. Ingestion inserts a new item, then stores its normalised, relevant candidates as `PENDING` with `next_check_at = now`.
2. `ResourceCheckJob` loads due candidates (`PENDING`/`CHECK_FAILED` with `next_check_at <= now`) for items still inside 72 hours.
3. For each: fresh cache hit → copy result. Otherwise ask the host's limiter; if allowed, call the API and write the cache.
4. Found → `VERIFIED` (GitHub may update `url`/`name` to the canonical repo). Missing → `NOT_FOUND`. Failed → `CHECK_FAILED`, `attempts++`, next delay; after 3 failures → `NOT_FOUND`.

## Notes from implementation
- Hosts are stored in upper case (`GITHUB`, `GITLAB`, `HUGGINGFACE`, `ARXIV`) to match the Java enum; the API's `type` values stay lower case as designed.
- The job depends on a small `LinkChecker` interface (implemented by `HostApis`) so tests can script host answers without a network.
- The per-host limiter is in memory. Only one instance runs the job at a time (database lock), so this is enough for one deployment; revisit if the job ever runs on several instances in turn.
- Candidates per item are capped at 10 before checking, to keep host calls small.

## Error handling
Host errors never affect ingestion or stories. Logs carry host, status and item id, never feed text.

## Testing approach
- `ResourceLinkNormaliserTest`: one row per line of the table above, plus case, `.git`, query strings and fragments.
- `ResourceRelevanceTest`: content links kept or dropped by name match.
- `FeedParserTest`: links found in description anchors, plain-text URLs and content anchors; `RawEntry` carries no content text.
- `HostApisIT`: WireMock per host for found, missing, renamed (GitHub 301), rate-limited, timeout; token header only when set; User-Agent sent.
- `ResourceCheckJobIT`: cache reuse across items, retry schedule, give-up after 3, limiter stops a batch, expired items skipped.
- `CatalogControllerIT`: `resources` present, verified only, ordered, max 3, labels; contract updated.
- Web: `ResourceLinks.test.tsx`; card tests show the row only when present; e2e and axe cover a story with links.

## Alternatives considered
- Asking the summary service to find links: it may invent plausible but wrong URLs. Rejected (principles).
- GitHub search by project name: wrong matches are likely for common names.
- Checking links by fetching the pages: page scraping. APIs are the documented route.

## Risks
- Many publishers don't link code in their feed teaser; coverage will be best for lab blogs, Hugging Face and research sources. The open question about extra sources addresses this.
- Unauthenticated GitHub limits are low. With `GITHUB_TOKEN` (a token with no scopes is enough) the limit rises well above our needs.

## Approved changes (2026-10-04)
Approved by Deb in session after the spec reviews. Where this section and the text above differ, this section wins.

- The "Notes from implementation" section is approved: hosts stored as written, at most 10 links per story.
- Links collected per feed entry are capped at 50 (entry links first, then description, then content).
- `FoundLink` and `LinkOrigin` are shared value types in `com.qbits.common.links`, used by ingestion and resources.
- Reserved GitHub first segments also include `resources, solutions, team, contact`. Reserved Hugging Face first segments are site pages only (`blog`, `docs`, `organizations`, listing pages). Organisation names such as `github` and `huggingface` are never reserved.
- `type` is an enum in the contract: `code | model | dataset | paper`.
- When a rename makes a link duplicate one the story already has, the rows merge into one verified row. One failing row never stops a check run; it goes on the normal retry schedule.
