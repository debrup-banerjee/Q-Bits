---
description: Review the implementation of a spec against its requirements, design and the steering rules
argument-hint: <NNN> [task id, e.g. T3]
---

Act as the reviewer described in `agents/reviewer.md`. Do not fix code during review.

Target: $ARGUMENTS

1. Read `specs/NNN-*/` (requirements, design, tasks, `.status`) and all of `steering/`.
2. Confirm `.status` is `tasks:approved`. If it is anything else, report that code was changed without approval and stop.
3. Run the full check from `steering/conventions.md` for the parts changed and record the result.
4. For each acceptance criterion in scope (all, or only those covered by the given task):
   - find the code that satisfies it and the test that proves it,
   - mark it PASS, FAIL or NO TEST.
5. Check every line of `steering/principles.md` that the change touches, and `steering/conventions.md`.
6. Look for work not in any task (scope creep) and design drift.
7. Write the report to `specs/NNN-*/review.md`:
   - verdict: APPROVE or CHANGES REQUESTED,
   - criteria table (id, status, code location, test name),
   - principle violations (always blocking),
   - convention issues (blocking or minor),
   - drift and unrequested work.

Do not change `.status`. A human sets `done` after reading the report.
