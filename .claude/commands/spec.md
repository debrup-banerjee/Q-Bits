---
description: Start a new feature spec, or move an approved spec to its next document
argument-hint: <NNN> | <feature-name: one-line description>
---

Act as the architect described in `agents/architect.md`. Read `steering/` in full first.

Input: $ARGUMENTS

## If the input is a new feature (not an existing spec number)
1. Pick the next free number in `specs/` and a kebab-case name.
2. Copy `specs/_template/` to `specs/NNN-feature-name/`.
3. Write `requirements.md`: behaviours and EARS-style acceptance criteria (`WHEN … THE SYSTEM SHALL …`), numbered R1.1, R1.2…. What, not how.
4. List open questions rather than guessing at scope.
5. Write `requirements:draft` to `.status`.

## If the input is an existing spec number
Read its `.status` and act:
- `requirements:approved` → write `design.md` from the template, tracing every component to requirement ids. Write `design:draft`.
- `design:approved` → tell the user to run `/tasks NNN`.
- Any `:draft` → stop. Say which file is waiting for human approval.
- `tasks:approved` or `done` → stop. Say there is nothing to specify.

## Always
- Do not write code or create application code.
- Never write an `:approved` value.
- Flag any conflict with `steering/principles.md` instead of designing around it.
- End by listing what the human needs to review and the exact `.status` value to set if they approve.
