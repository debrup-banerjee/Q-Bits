# 009: A picture for every story

Owner decision, 2026-10-07: build now, outside the usual spec-first order (asked in the session).
This file records what was built so tests can point at it. Review it and set `.status`.

## Why
Stories are text-only. A picture per story makes the feed easier to scan, like other AI newsletters,
without copyright risk: no publisher photo is ever shown without recorded permission.

## Requirements

### R1 Cover art (always available)
- R1.1 A story with no picture shows cover art drawn by the client from its section, words and id. The same story always gets the same art; different stories get different patterns.
- R1.2 The art's glyph is picked from the story's own words (chip, robot, chart, flask, shield, code, globe, spark). No text, logos or real-world marks.
- R1.3 Cover art is decorative (`aria-hidden`).

### R2 Pexels photos
- R2.1 A background job asks the model for a short photo search phrase, sending only Q-Bits' own headline, summary and key-term names.
- R2.2 A phrase is used only if it is 3-60 characters of plain lower-case words and contains no name from the story (capitalised headline words, key terms with capitals or digits). Otherwise R2.3 applies.
- R2.3 Fallback phrase: a known neutral visual for the first matching key term, else a neutral scene for the section.
- R2.4 The first result that is https, has a named photographer and was not used in the last 7 days is chosen. If the model phrase finds nothing, the fallback phrase is tried once.
- R2.5 Search asks for landscape photos and uses Pexels' own landscape crop, hotlinked from Pexels' CDN, never downloaded.
- R2.6 At most `qbits.images.pexels.per-hour` calls an hour (150 by default, under Pexels' 200 an hour and 20,000 a month); model calls capped per day.
- R2.7 Without `QBITS_PEXELS_API_KEY`, no search runs and stories keep their cover art.
- R2.8 Not Unsplash: its API guidelines allow only "non-automated" use, and this job picks photos automatically (owner decision, 2026-10-07).

### R3 Publisher images, only with permission
- R3.1 A source may carry an `images` block in `config/sources.yml` with `basis` (press_kit or written_permission), `evidence` URL, `reviewedOn` and `credit`. All four are required; a person writes it after checking the terms.
- R3.2 The feed parser reads the image the feed attaches to an entry (image enclosure, Media RSS content or thumbnail); https only. Its URL is stored only for sources with an `images` block. Images in description or content HTML are never used.
- R3.3 For those sources the feed image is used before any photo search and credited as "Image: <credit>".

### R4 Storage
- R4.1 Only URLs, credits, alt text, colour and the search phrase are stored; rows are deleted with their item.
- R4.2 When nothing safe is found the story is marked NONE and shows cover art.
- R4.3 A failed search is retried after 30 minutes and 3 hours, then marked NONE.

### R5 Display
- R5.1 The API's `StoryView.image` carries kind, url, alt, credit, creditUrl, provider, providerUrl and colour; null when there is no picture.
- R5.2 Every picture shows its credit right under it: "Illustrative photo by <photographer> on Pexels" (both linked) or "Image: <credit>". Every page's footer links to Pexels ("Photos provided by Pexels"), as Pexels asks.
- R5.3 A picture that fails to load is replaced by cover art.
- R5.4 Pages with pictures pass the WCAG 2 A/AA checks in light and dark mode.

### R6 Link previews
- R6.1 A story page with a picture sets `og:image`, a large Twitter card and the JSON-LD `image` with its credit.
