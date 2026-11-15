# Specs

One folder per feature: `NNN-feature-name/` (three-digit number, kebab-case name).

| File | Purpose |
|---|---|
| `requirements.md` | Behaviours and acceptance criteria. What, not how. |
| `design.md` | Architecture, data model, contracts. How. |
| `tasks.md` | Units of 2–4 hours, each independently testable. |
| `.status` | The approval gate. A single line, one of the values below. |

## `.status` lifecycle

```
requirements:draft → requirements:approved
  → design:draft → design:approved
  → tasks:draft → tasks:approved   ← implementation allowed only here
  → done
```

- The agent may write any `*:draft` value.
- Only a human writes `*:approved` or `done`.
- A human may review requirements, design and tasks together and go straight to `tasks:approved`.
- Changing an approved file sends the status back to that stage's `:draft`.

Copy `_template/` to start a new spec, or run `/spec`.
