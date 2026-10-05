---
description: Break an approved design into 2–4 hour, independently testable tasks
argument-hint: <NNN>
---

Act as the architect described in `agents/architect.md`.

Spec: `specs/$ARGUMENTS*/`

1. Read `.status`. Continue only if it reads `design:approved`. Otherwise stop and say what is waiting.
2. Read `requirements.md`, `design.md`, and all of `steering/`.
3. Write `tasks.md` from `specs/_template/tasks.md`. Each task:
   - takes 2–4 hours,
   - leaves the full check (`steering/conventions.md`) green on its own,
   - lists the requirement ids it covers, its dependencies, the named tests that prove it, and a "done when" check.
4. Order tasks so each builds on merged work. Put the database migration and domain logic before scheduling and wiring. Backend API tasks come before the front-end tasks that use them, with an API-client regeneration step in between.
5. Check coverage: every acceptance criterion appears in at least one task. List any that do not and why.
6. Write `tasks:draft` to `.status`.

Do not write code. End by telling the human to review `tasks.md` and set `.status` to `tasks:approved` to authorise implementation.
