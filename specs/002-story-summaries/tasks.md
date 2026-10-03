# 002 — Story summaries: Tasks

Each task is 2–4 hours, leaves the full check green, and can be tested on its own.

## T1 — Schema and domain types
- **Covers:** R3.1, R10.1
- **Depends on:** 001 T3
- **Do:** `V2__stories.sql`; `Section` enum; `StoryInput`, `StoryDraft`, `KeyTerm` records; `StoryRepository`.
- **Tests:** `StoryRepositoryIT` (save/load key terms JSON, section check constraint).
- **Done when:** migration applies on top of V1.
- **Status:** done

## T2 — Story validator
- **Covers:** R4.1, R4.2, R4.4, R5.1, R5.3, R6.1, R7.1, R7.2, R8.1
- **Depends on:** T1
- **Do:** Pure `StoryValidator` implementing the rule table.
- **Tests:** `StoryValidatorTest`, a pass and a fail case per rule id, including "1,000" vs "1000" and the 40-word short-excerpt case.
- **Done when:** all rules covered; no Spring dependency.
- **Status:** done

## T3 — Writer interface, fake and service
- **Covers:** R2.1, R2.2, R8.2, R8.3, R8.4
- **Depends on:** T2
- **Do:** `StoryWriter`, `FakeStoryWriter`, `StoryService` (write → validate → retry with feedback → save/status).
- **Tests:** `StoryServiceTest` (publish, NOT_AI with reason, retry pass, retry reject with rule, unavailable leaves PENDING).
- **Done when:** all status transitions proven.

## T4 — Anthropic writer
- **Covers:** R1.2, R3.2–R3.6, R4.3, R4.5, R5.2, R5.4, R6.2 (via prompt), R10.1
- **Depends on:** T3
- **Do:** Add `com.anthropic:anthropic-java`; `AnthropicStoryWriter` with prompt file `story-writer.v1.md`, forced `write_story` tool, token counts, error mapping; config record.
- **Tests:** `AnthropicStoryWriterTest` against WireMock base URL: request contains only allowed fields, tool forced, model from config; 429/500/timeout → `WriterUnavailable`; 400 → `WriterRejected`.
- **Done when:** tests pass with no real key.

## T5 — Story job, budget, expiry
- **Covers:** R1.1, R1.3, R9.1–R9.4
- **Depends on:** T3
- **Do:** `StoryJob` with ShedLock, newest-first batch, parallelism, backoff; `DailyBudget`; 72 h expiry.
- **Tests:** `StoryJobIT` with `FakeStoryWriter` and fixed `Clock`: order, expiry, cap stops the day, parallel limit, writer down leaves ingestion working.
- **Done when:** all pass.

## T6 — Operator commands
- **Covers:** R10.2
- **Depends on:** T5
- **Do:** `--rewrite-story=<id>` and `--rewrite-source=<id>` under `admin` profile.
- **Tests:** `StoryAdminIT`.
- **Done when:** rewrite replaces story and records new prompt version.

## T7 — Live sample and editorial check
- **Covers:** R4.3, R4.5, R5.2, R5.4, R6.2 (human-judged), non-functional publish time
- **Depends on:** T4, T5
- **Do:** Opt-in `StoryWriterLiveIT` on 20 fixture items (5 per section); write outputs to `build/live-samples.md`; run with two model sizes and record cost per 100 stories.
- **Tests:** the live suite itself (excluded from the default build).
- **Done when:** owner reviews the samples against `steering/editorial-style.md` and picks the model; prompt adjusted only as a new version file if needed.
