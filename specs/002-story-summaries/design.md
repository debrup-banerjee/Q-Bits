# 002 — Story summaries: Design

## Overview
A scheduled `StoryJob` takes `PENDING` items (newest first), sends each one's title and excerpt to the summary service through the `StoryWriter` interface, validates the structured result with `StoryValidator`, and either publishes the story, marks it `NOT_AI`, retries once with feedback, or marks it `REJECTED`. The first implementation, `AnthropicStoryWriter`, uses the official Anthropic Java SDK with a forced tool call so the answer always comes back as JSON matching a schema.

## Components
| Component | Package | Responsibility | Requirements |
|---|---|---|---|
| `StoryJob` | `stories` | `@Scheduled` (every 2 min) + ShedLock; picks up to N `PENDING` items newest first; expires items > 72 h; enforces daily cap and parallelism. | R1.1, R9 |
| `StoryInput` | `stories.domain` | Record of exactly the fields allowed out: title, excerpt, sourceName, region, sectionHint, publishedAt. | R1.2 |
| `StoryWriter` | `stories` | Interface: `StoryDraft write(StoryInput in, Optional<String> feedback)`. | R1 |
| `AnthropicStoryWriter` | `stories.anthropic` | SDK call: system prompt from `prompts/story-writer.v1.md`, user message = `StoryInput` as JSON, tool `write_story` with the schema below, `tool_choice` forced. Maps errors to `WriterUnavailable` / `WriterRejected`. | R1–R5, R9.2, R10.1 |
| `FakeStoryWriter` | test | Deterministic drafts and scripted failures for tests. | — |
| `StoryValidator` | `stories.domain` | Pure checks: shape, section, word counts, number guard, 8-gram overlap, quotes, key-term presence, acronym spelled-out heuristic. Returns list of failed rules. | R4–R8 |
| `StoryService` | `stories` | Orchestrates write → validate → retry once → save; transitions status. | R8 |
| `DailyBudget` | `stories` | Counts calls per UTC day in DB; blocks when cap reached. | R9.1 |
| `StoryAdminCommands` | `stories` | `--rewrite-story=<id>`, `--rewrite-source=<id>` under the `admin` profile. | R10.2 |

## Data model (migration `V2__stories.sql`)
```sql
create table stories (
  item_id        uuid primary key references items(id) on delete cascade,
  section        text not null check (section in ('GLOBAL_AI_TECH','WORLD_BUSINESS','INDIA_AI','INNOVATIONS_RESEARCH')),
  headline       text not null check (char_length(headline) <= 120),
  summary        text not null check (char_length(summary) <= 1000),
  key_terms      jsonb not null default '[]',      -- [{ "term": "...", "meaning": "..." }]
  model          text not null,
  prompt_version text not null,
  written_at     timestamptz not null,
  input_tokens   int not null,
  output_tokens  int not null,
  attempts       int not null
);
alter table items add column story_note text;   -- NOT_AI reason or REJECTED rule
-- items.story_status gains: NOT_AI | REJECTED | EXPIRED (in addition to PENDING | PUBLISHED)
create table writer_budget (day date primary key, calls int not null default 0);
create index stories_section_idx on stories (section);
```

## Contracts
**Config**
```yaml
qbits:
  story-writer:
    provider: anthropic
    model: ${QBITS_STORY_MODEL}     # set to a current Claude model at deploy time; compare a small and a mid-size model in T7
    prompt-version: v1
    max-output-tokens: 800
    daily-call-cap: 500
    parallelism: 4
    batch-size: 20
    timeout: PT30S
```
`ANTHROPIC_API_KEY` from the environment only.

**Tool schema `write_story` (structured output)**
```json
{
  "type": "object",
  "required": ["isAi", "reason"],
  "properties": {
    "isAi":     { "type": "boolean" },
    "reason":   { "type": "string", "maxLength": 160 },
    "section":  { "enum": ["GLOBAL_AI_TECH", "WORLD_BUSINESS", "INDIA_AI", "INNOVATIONS_RESEARCH"] },
    "headline": { "type": "string" },
    "summary":  { "type": "string" },
    "keyTerms": {
      "type": "array", "maxItems": 5,
      "items": { "type": "object", "required": ["term", "meaning"],
                 "properties": { "term": { "type": "string" }, "meaning": { "type": "string" } } }
    }
  }
}
```
When `isAi` is true, `section`, `headline` and `summary` are required (checked by `StoryValidator`).

**Prompt `backend/src/main/resources/prompts/story-writer.v1.md` (draft)**
```
You write short news stories about AI for a general audience. Readers range from people
with no tech background to experienced engineers. Both must understand every sentence,
and neither should feel talked down to.

You receive one news item: the publisher's headline and short teaser, the source name,
region, a section hint and the publish date. You have nothing else. Use only facts in
this input. Do not add numbers, names, dates, specs or background from your own knowledge.

1. Decide if the item is mainly about AI, or technology that directly drives AI
   (chips, data centres, AI policy). If not, set isAi=false with a one-line reason and stop.
2. Pick one section:
   - INDIA_AI if the story has a real India angle, whatever the topic: an Indian company,
     startup, institution or government body; a global company's launch, investment,
     plant, hiring or pricing in India; Indian policy; Indian research. A passing
     mention (India in a list of countries) does not count.
   - else INNOVATIONS_RESEARCH for research, lab results, not-yet-released innovations.
   - else WORLD_BUSINESS for money, companies, deals, jobs, chips/plants, markets, regulation
     outside India.
   - else GLOBAL_AI_TECH for models, products, versions, capabilities and technical specs.
   The section hint is only a hint.
3. Headline: at most 12 words, plain and specific, in your own words.
4. Summary: 80–120 words (40–120 if the teaser is very short). Tell it as a small story:
   what happened, why it matters, who it affects or what to watch. Open with the most
   interesting fact. Short sentences, everyday words, active voice. Name the source once
   ("according to <source>"). Keep the source's hedges ("plans to", "says").
5. Keep every important AI or tech term from the input. Never swap it for a vague word.
   Explain it simply the first time. Spell out acronyms on first use, e.g.
   "graphics processing units (GPUs)". Keep numbers and units exactly as given.
6. keyTerms: up to 5 terms a curious reader should learn from this story, each explained
   in under 20 everyday words.
7. Write everything in your own words. Never copy a sentence or a distinctive phrase from
   the teaser, and never quote it. No opinions, predictions or investment advice.

Return the result only through the write_story tool.
```
On retry the user message adds: `Your previous answer broke these rules: <rules>. Fix them.`

## Flow
1. `StoryJob` acquires lock → marks `PENDING` items older than 72 h as `EXPIRED` → checks `DailyBudget` → loads up to `batch-size` `PENDING` items newest first.
2. For each (max `parallelism` at once): build `StoryInput` → `StoryWriter.write` → `DailyBudget.increment`.
3. `isAi=false` → status `NOT_AI`, `story_note = reason`.
4. Else `StoryValidator.check(draft, input)` → no failures → insert `stories` row, status `PUBLISHED`.
5. Failures → one retry with feedback → pass: publish; fail: status `REJECTED`, `story_note = rules`.
6. `WriterUnavailable` (5xx, 429, timeout) → leave `PENDING`, stop the batch, back off (2, 4, 8… up to 30 min).

## Validation rules (`StoryValidator`)
| Rule id | Check | Req |
|---|---|---|
| `SHAPE` | required fields present when `isAi`; section in enum | R3.1, R8.1 |
| `HEADLINE_LEN` | ≤ 12 words | R4.1 |
| `SUMMARY_LEN` | 80–120 words, or 40–120 when excerpt < 150 chars | R4.2 |
| `SOURCE_NAMED` | summary contains source name | R4.4 |
| `TERMS` | ≤ 5; meaning ≤ 20 words; term appears in title/excerpt/summary | R5.1, R5.3 |
| `NUMBERS` | every number token in headline+summary appears in title or excerpt (normalising "1,000"/"1000") | R6.1 |
| `OVERLAP` | no shared 8-word sequence with title/excerpt after lower-casing and stripping punctuation; product names excluded | R7.1 |
| `QUOTES` | no quoted span of 4+ words that appears in the excerpt | R7.2 |

R4.3, R4.5, R5.2, R5.4 and R6.2 are judged by the prompt and checked by review on a sample (see Testing), since they cannot be fully machine-checked.

## Error handling
SDK exceptions map to `WriterUnavailable` (retry later) or `WriterRejected` (bad request → `REJECTED` with `PROVIDER_ERROR`). Prompts and responses are never logged at INFO; DEBUG logging of them is off by default.

## Testing approach
- `StoryValidatorTest`: table-driven, one passing and one failing case per rule.
- `StoryServiceTest` with `FakeStoryWriter`: publish, NOT_AI, retry-then-pass, retry-then-reject, unavailable leaves PENDING.
- `StoryJobIT` (Testcontainers): newest-first order, expiry at 72 h, daily cap, parallelism, ingestion unaffected while writer down.
- `AnthropicStoryWriterTest`: request built correctly from a WireMock-backed base URL (only allowed fields sent; tool forced).
- `StoryWriterLiveIT` (opt-in, needs key): 20 fixture items from different sections; outputs saved to `build/live-samples.md` for the editorial review in T7.

## Alternatives considered
- Separate calls for relevance, section and summary — 3× cost and latency, no quality gain.
- Free-text output parsed with regex — fragile; forced tool call gives schema-valid JSON.
- Summarising full articles — rejected on legal grounds (principles).

## Risks
- Thin excerpts give thin stories. Mitigation: shorter minimum length, never pad, prominent link out.
- The model may still slip in outside knowledge that has no numbers. Mitigation: prompt rule, sample reviews, reviewer checklist.
- Cost grows with source count. Mitigation: pre-filter (001), daily cap.

## Approved changes (2026-10-04)
Approved by Deb in session after the spec reviews. Where this section and the text above differ, this section wins.

- Prompt: `prompts/story-writer.v2.md` (v1 plus: keep every product, model and company name exactly as the input writes it); `prompt-version: v2`. v1 is kept unchanged.
- Inputs sent (R1.2) are unchanged; `steering/principles.md` now states that the only publisher text sent is the title and excerpt, plus plain facts (source name, publish date, our region and section hints).
- Validation: headline ≤ 120 and summary ≤ 1000 characters after trimming (same limits as the database), so an over-long draft is invalid instead of failing on save. Quote check: an apostrophe inside a word is not a quote mark. `NUMBERS`: every number in headline+summary appears in the title or excerpt (1,000 = 1000); a number followed by a scale word (thousand/k, million/mn/m, billion/bn/b, trillion/tn, lakh, crore) must appear with the same scale. Other units (GB, nm) are judged in the editorial sample check.
- `story_note` is one line of at most 300 characters; longer reasons are cut cleanly.
- Error handling: any other client error (for example an answer that cannot be read) maps to `WriterUnavailable`.
- The extra back-off table used by the story job is part of the design.
- Stories reach items only through `ingestion.ItemStore`, never `ingestion.persistence`; a test enforces this.
