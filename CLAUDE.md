# Q-Bits

AI news from the last 72 hours, worldwide, in plain-language stories with links to the originals. Read `steering/product.md` before any work.

## Where things live
- `steering/` — product, tech-stack, conventions, principles, editorial-style (how summaries are written)
- `specs/NNN-feature-name/` — requirements, design, tasks and `.status` per feature
- `agents/` — role prompts: architect, implementer, reviewer
- `.claude/commands/` — `/spec`, `/tasks`, `/review`

## Order of work
requirements → design → tasks → approval → implement → review against the spec.

## Approval gate (check before touching code)
1. Before creating or changing application code (`backend/`, `web/`, `mobile/`, `packages/`), read `specs/NNN-*/.status`.
2. Implement only when it reads exactly `tasks:approved`. Otherwise stop, say which stage is waiting, write no code.
3. Never write an `:approved` value or `done` yourself — only a human does. You may write `*:draft`.
4. Work outside a spec (typo, dependency bump) still needs the human to say so in the session.

## Non-negotiables
- Summaries are written in our own words from the feed's headline and excerpt only. Never fetch, store or summarise article pages.
- Every story shows its source and a "Read the full story" link. No iframes, in-app web views or reader view. No images or logos.
- Fetch only sources in `config/sources.yml` with reviewed terms, via RSS/Atom/API, after checking robots.txt.
- Daily digest: feeds are read all day, summaries are written once a day in one batch, and an edition goes live all at once. Tabs: AI Latest first (today's edition, social-style feed), then four sections only: Global AI Tech, World Business, India AI, Innovations & Research (last three editions).
- Open-source links come only from URLs in the feed entry, on allowlisted hosts, checked via the host's API. Never guess or generate a link.
- `steering/principles.md` overrides any spec. If they conflict, stop and flag it.
- Stack: Java + Spring Boot backend, React + TypeScript front end. Details in `steering/tech-stack.md`.
- Git: HTTPS only, never SSH. Commit email `debrup28.nitdgp@gmail.com`. Confirm before any push.
