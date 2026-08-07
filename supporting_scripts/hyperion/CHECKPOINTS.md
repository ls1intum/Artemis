# Hyperion turn checkpoints

This development-only recorder makes every effectful authoring turn a restorable point. It captures the exact prompt (including tool-call IDs and results), loop
budget/failure state, stage and verification authority, the approved specification, and the mutable sandbox roots. Production does nothing unless a checkpoint directory or
replay source is configured.

## What drives it

The server side lives in `AgentCheckpointManager` and is inert unless one of these is set. `supporting_scripts/hyperion/checkpoint` sets them for you through their
`ARTEMIS_*` environment forms; you only need them directly to reproduce a run by hand, or to explain a server that is unexpectedly replaying instead of sampling.

| Property                                                | Default | Effect                                                                                     |
| ------------------------------------------------------- | ------- | ------------------------------------------------------------------------------------------ |
| `artemis.hyperion.agent.checkpoint-dir`                 | unset   | Record every authoring call into this directory. Unset means no recording and no overhead. |
| `artemis.hyperion.agent.checkpoint-replay-from`         | unset   | Replay the recorded run at this path instead of calling the provider.                      |
| `artemis.hyperion.agent.checkpoint-fork-at`             | `0`     | Author call at which a replay stops restoring and resumes live sampling.                   |
| `artemis.hyperion.agent.checkpoint-fork-review-at`      | `0`     | Reviewer call at which to fork instead.                                                    |
| `artemis.hyperion.agent.checkpoint-fork-instruction`    | unset   | Extra labelled user instruction injected only at the fork call.                            |
| `artemis.hyperion.agent.checkpoint-strict`              | `false` | Fail closed on secret material, links, path traversal, unsupported files, or oversized roots rather than writing a partial bundle. The CLI always sets this. |

Bundles contain full prompts, model responses, and generated source. Keep the default ignored local store or apply equivalent access controls; do not publish them as CI
artifacts.

## Commands

`record`, `replay`, and `fork` restart the local server so its checkpoint properties cannot silently remain stale, reuse or start the Angular client, and drive a single brief
through `src/test/playwright/e2e/exercise/programming/HyperionCheckpointDriver.spec.ts`. They need a reachable Postgres on `localhost:5432`, a usable Docker socket for the
sandbox, free ports 8080 and 9000, and live provider credentials. Provider configuration can live in the git-ignored `.env.hyperion.local` at the repository root. Artifacts
default to `.e2e-local/hyperion-checkpoints/`; server and client logs go to `.e2e-local/checkpoint-{server,client}.log`.

`turns`, `show`, and `compare` are offline and need only the checkpoint directory.

```bash
supporting_scripts/hyperion/checkpoint record /tmp/elevator-brief.md --name elevator-base
supporting_scripts/hyperion/checkpoint turns elevator-base
supporting_scripts/hyperion/checkpoint show elevator-base@42
supporting_scripts/hyperion/checkpoint show elevator-base@r3

# No provider/tool calls are repeated in the recorded prefix.
supporting_scripts/hyperion/checkpoint replay elevator-base

# Restore call 42's pre-state, sample live from there, and continue through the pipeline.
supporting_scripts/hyperion/checkpoint fork elevator-base@42 --name prompt-v2 --repeat 5

# Change only the selected late call while preserving the recorded prefix.
supporting_scripts/hyperion/checkpoint fork elevator-base@42 --name prompt-v3 --instruction-file /tmp/experiment.txt --repeat 5

# Inspect where two checkpoint traces first diverge.
supporting_scripts/hyperion/checkpoint compare elevator-base prompt-v2
```

`RUN@N` selects an author call, `RUN@rN` a reviewer call. `--instruction-file` is supported only for author-call forks.

## Semantics

Replay is deterministic and needs no configured AI provider: every authoring call restores its committed post-state without invoking the provider or tools, while reviewers
return their recorded verdict. Deterministic orchestration between calls (stage gates and differential verification) runs normally, which validates restored state and returns a
fork to the correct pipeline continuation. A fork begins live sampling at the selected author call, including later reviewers, and therefore requires the same provider
model/options as its source. Forks are fresh samples, not controlled deterministic experiments; `--repeat` is useful for exploratory debugging.
`--instruction-file` appends a clearly labelled user instruction only at the selected call, so a late-stage experiment does not invalidate or rerun the prefix.

The unit is deliberately the **effectful agent turn**, not a transport request: an empty-response retry has no new state to restore, and an internal context-compaction request is
part of preparing that turn's next recorded prompt. Every point where an assistant response can execute tools or alter orchestration state is independently addressable.
Wall-clock deadlines are safety controls rather than replayed state; compare live suffix work (turns, tools, tokens, verification and outcome), and do not use a deadline-censored
source for causal fork comparisons.

## Scope

This is a debugging tool, not an evaluation harness. Comparative campaigns, datasets, grading, usage reconciliation, distributional comparisons, and release archives are out of
scope and are not part of this repository. This tool only records, replays, and forks Hyperion's internal effectful turns.

Safety rules:

- Checkpoint bundles are content-addressed and integrity-checked; inspection aborts on a checksum mismatch.
- Tool-schema, loop-budget, and replay-prefix drift fail closed.
- Prompt drift is allowed only at the selected fork call.
- Secret material, links, path traversal, unsupported files, and oversized roots make strict development recording fail instead of yielding a partial checkpoint.
- Bundle files are committed atomically; inspection ignores unfinished runs.
</content>
