# Artemis agent skills

Skills that teach an AI coding agent how this repository actually works: which tests a change
needs, why a build is red, and which conventions the build enforces.

`CLAUDE.md` holds facts and is always in the agent's context. These skills hold procedures and load
only when they are used, which is why they can be long.

## Installing

Any agent (Claude Code, Cursor, Codex, Copilot, opencode, Zed, and around seventy others):

```bash
npx skills add ls1intum/Artemis
```

Claude Code, as a versioned plugin with namespaced skills (`/artemis:e2e-pr-check`):

```text
/plugin marketplace add ls1intum/Artemis
/plugin install artemis@artemis
```

## The skills

| Skill                 | What it does                                                                    |
| --------------------- | ------------------------------------------------------------------------------- |
| `e2e-pr-check`        | Runs only the Playwright specs a change affects, and reads the result correctly |
| `ci-triage`           | Classifies a red build before anyone changes code                               |
| `server-arch-gates`   | Maps a server change to the architectural rules it must satisfy                 |
| `liquibase-migration` | Writes a changelog that survives a rolling deploy on both databases             |
| `client-conventions`  | Angular signal APIs, cloning, template control flow, TUM UI styling             |
| `write-tests`         | Base class selection, and the test commands that silently do the wrong thing    |
| `local-setup`         | Fresh clone to a running server and client                                      |

## Contributing a skill

A skill is a directory under `skills/` containing `SKILL.md`, plus optional `reference/` files that
the body points at.

```markdown
---
name: my-skill
description: What it does and when to use it, key use case first.
---

# Title

The procedure.
```

Rules for this repository:

- **Every factual claim cites a repository path.** `supporting_scripts/check_skill_references.py`
  runs as the `Agent Skills` CI job and fails if a path referenced from `skills/` no longer exists.
  It reads inline code spans and fenced code blocks, so example commands are checked too.
- **Keep the body a procedure.** Long background belongs in a `reference/` file, which costs
  nothing until the agent reads it.
- **Say why, not just what.** A rule without its reason gets worked around rather than followed.
- **Do not restate `CLAUDE.md`.** Add the part that does not fit there: the steps, the commands,
  the failure modes.

Test a skill before opening a pull request:

```bash
claude --plugin-dir .                                          # loads the working copy
python3 supporting_scripts/check_skill_references.py           # citations still resolve
python3 supporting_scripts/check_skill_references.py --self-test  # the checker itself still works
claude plugin validate .
```

Documentation for users: `documentation/docs/developer/work-with-ai.mdx`.
