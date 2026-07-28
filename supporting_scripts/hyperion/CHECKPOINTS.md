# Hyperion turn checkpoints

This development-only recorder makes every authoring model call a restorable point. It captures the exact prompt (including tool-call IDs and results), loop budget/failure state,
stage and verification authority, the approved specification, and the mutable sandbox roots. Production does nothing unless a checkpoint directory or replay source is configured.

```bash
# Provider credentials/config can live in the ignored .env.hyperion.local file.
supporting_scripts/hyperion/checkpoint record seeded-strategy-elevator --name elevator-base
supporting_scripts/hyperion/checkpoint turns elevator-base
supporting_scripts/hyperion/checkpoint show elevator-base@42
supporting_scripts/hyperion/checkpoint show elevator-base@r3

# No provider/tool calls are repeated in the recorded prefix.
supporting_scripts/hyperion/checkpoint replay elevator-base

# Restore call 42's pre-state, sample live from there, and continue through the pipeline.
supporting_scripts/hyperion/checkpoint fork elevator-base@42 --name prompt-v2 --repeat 5
supporting_scripts/hyperion/checkpoint compare elevator-base prompt-v2
```

`record`, `replay`, and `fork` restart the local server so its checkpoint properties cannot silently remain stale, reuse/start the Angular client, and drive the tracked
`HyperionLiveLlmUI.spec.ts` scenario. They expect a reachable local database and the ordinary Hyperion live-provider variables. Artifacts default to
`.e2e-local/hyperion-checkpoints/`.

Replay is deterministic and needs no configured AI provider: every authoring call restores its committed post-state without invoking the provider or tools, while reviewers
return their recorded verdict. Deterministic orchestration between calls (stage gates and differential verification) runs normally, which validates restored state and returns a
fork to the correct pipeline continuation. A fork begins live sampling at the selected author call, including later reviewers, and therefore requires the same provider
model/options as its source. Forks are fresh samples, not controlled deterministic experiments; use `--repeat` and compare distributions when the provider has no seed.

Safety rules:

- Checkpoint bundles are content-addressed and integrity-checked.
- Tool-schema, loop-budget, and replay-prefix drift fail closed.
- Prompt drift is allowed only at the selected fork call.
- Secret material, links, path traversal, unsupported files, and oversized roots make strict development recording fail instead of yielding a partial checkpoint.
- Bundle files are committed atomically; inspection ignores unfinished runs.
- Bundles contain full prompts, model responses, and generated source. Keep the default ignored local store or apply equivalent access controls; do not publish them as CI artifacts.
