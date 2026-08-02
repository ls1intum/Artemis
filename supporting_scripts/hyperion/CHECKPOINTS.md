# Hyperion turn checkpoints

This development-only recorder makes every effectful authoring turn a restorable point. It captures the exact prompt (including tool-call IDs and results), loop
budget/failure state, stage and verification authority, the approved specification, and the mutable sandbox roots. Production does nothing unless a checkpoint directory or
replay source is configured.

```bash
# Provider credentials/config can live in the ignored .env.hyperion.local file.
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
# Inspect where two checkpoint traces first diverge; use exgen-bench for measured comparisons.
supporting_scripts/hyperion/checkpoint compare elevator-base prompt-v2
```

`record`, `replay`, and `fork` restart the local server so its checkpoint properties cannot silently remain stale, reuse/start the Angular client, and drive a single brief through
`HyperionCheckpointDriver.spec.ts`. They expect a reachable local database and the ordinary Hyperion live-provider variables. Artifacts default to
`.e2e-local/hyperion-checkpoints/`.

The driver is intentionally not an evaluation harness. Comparative campaigns, datasets, grading, usage reconciliation, and release archives belong in exgen-bench. This tool
exists only to record, replay, and fork Hyperion's internal effectful turns.

Replay is deterministic and needs no configured AI provider: every authoring call restores its committed post-state without invoking the provider or tools, while reviewers
return their recorded verdict. Deterministic orchestration between calls (stage gates and differential verification) runs normally, which validates restored state and returns a
fork to the correct pipeline continuation. A fork begins live sampling at the selected author call, including later reviewers, and therefore requires the same provider
model/options as its source. Forks are fresh samples, not controlled deterministic experiments; `--repeat` is useful for exploratory debugging, while distributional comparisons
belong in exgen-bench.
`--instruction-file` appends a clearly labelled user instruction only at the selected call, so a late-stage experiment does not invalidate or rerun the prefix.

The unit is deliberately the **effectful agent turn**, not a transport request: an empty-response retry has no new state to restore, and an internal context-compaction request is
part of preparing that turn's next recorded prompt. Every point where an assistant response can execute tools or alter orchestration state is independently addressable.
Wall-clock deadlines are safety controls rather than replayed state; compare live suffix work (turns, tools, tokens, verification and outcome), and do not use a deadline-censored
source for causal fork comparisons.

Safety rules:

- Checkpoint bundles are content-addressed and integrity-checked.
- Tool-schema, loop-budget, and replay-prefix drift fail closed.
- Prompt drift is allowed only at the selected fork call.
- Secret material, links, path traversal, unsupported files, and oversized roots make strict development recording fail instead of yielding a partial checkpoint.
- Bundle files are committed atomically; inspection ignores unfinished runs.
- Bundles contain full prompts, model responses, and generated source. Keep the default ignored local store or apply equivalent access controls; do not publish them as CI artifacts.
