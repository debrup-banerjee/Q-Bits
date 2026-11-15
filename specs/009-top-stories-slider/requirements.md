# 009 — Top stories slider: Requirements

## Summary
Readers want the biggest news first. At the top of AI Latest, show a slider of the three stories
that the most different outlets reported in the latest edition. "Most reported" is read from the
duplicate groups spec 008 already builds, so this feature makes **no extra summary-service call
and sends no extra tokens**. It is a ranking over data we already store.

## In scope
- A coverage figure per published story: how many different sources reported the same event
- The top 3 stories by that count, returned by the API and shown as a slider on AI Latest

## Out of scope
- Any new summary-service call, prompt or model (cost: zero tokens)
- Social, trending or click-based signals (no external APIs; no reader tracking in v1)
- Realtime or intra-day "breaking" alerts; editions stay once a day
- A slider on section tabs
- Images, autoplay carousels, or links to the held-back outlets (spec 008 follow-up)

## Behaviours and acceptance criteria

### R1 — Coverage count
- **R1.1** THE SYSTEM SHALL define a published story's coverage as the number of distinct sources
  among the story itself and the `DUPLICATE` stories whose `duplicate_of` is that story
  (spec 008). A story with no duplicates has coverage 1.
- **R1.2** Coverage SHALL be derived from stored data when requested, never stored, so a copy held
  back by a later edition or by `--dedupe-now` counts at once. No summary-service call SHALL be
  made for it.
- **R1.3** Coverage SHALL NOT change when the same data is read twice.

### R2 — Pick the top 3
- **R2.1** `GET /api/v1/stories/top` SHALL return at most 3 published stories from the latest
  edition's 24-hour window (the AI Latest window), ordered by coverage, then by an `official`
  source, then by the earliest publish time, then by id.
- **R2.2** Only stories with coverage of 2 or more SHALL be returned. IF fewer than 3 qualify THEN
  the API SHALL return fewer (possibly none).
- **R2.3** The response SHALL use the existing story card shape plus `coverage`, and the endpoint
  SHALL be cacheable for the life of the edition.
- **R2.4** Duplicates SHALL NOT appear (spec 008 R2.4).

### R3 — Slider on AI Latest
As a reader, I want the biggest stories at the top, so that I see what matters in seconds.

- **R3.1** AI Latest SHALL show the slider above the feed, titled "Top stories".
- **R3.2** Each slide SHALL show the headline (linking to `/story/:id`), the source badge and name,
  the section label, the first two lines of the summary, and "Covered by N outlets" (plain text, no
  outlet links).
- **R3.3** The slider SHALL use native horizontal scrolling with snap points, plus previous/next
  buttons and dots. It SHALL NOT autoplay, and SHALL work with keyboard, touch and screen readers
  (`aria-roledescription="carousel"`, slide labels "1 of 3").
- **R3.4** IF the API returns no stories THEN THE slider SHALL NOT render, and the feed SHALL look
  as it does today.
- **R3.5** The slider SHALL add no images, logos, embeds or third-party scripts, and no slider
  library. It SHALL stay readable at 360px with no horizontal page scroll.
- **R3.6** A story in the slider SHALL still appear in the feed below it.

## Non-functional
- One small indexed query per request, cached for the life of the edition; no per-reader work.
- The slider adds under 2 KB gzipped of client code (no dependency).

## Principles touched
- "Readers never trigger a call to the summary service": none is made.
- "An edition becomes visible all at once": the slider reads only published stories of the latest
  edition's window.
- "Clients hold no business rules": ranking lives in the backend; the client only displays.
- No images or publisher logos; every card keeps source, "Read the full story" and attribution.

## Decisions (owner)
- Label is "Top stories".
- A story qualifies with coverage of 2 or more outlets.
- A story reported by one official source alone (coverage 1) never qualifies.

## Open questions
- None.
