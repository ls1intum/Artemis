# Sizing and models

Each role names a **size**, not a model. Map sizes to the models your harness offers at the start of the run, write the mapping into `state.md`, and keep it for the whole run.

| Size | What it must do well | Reasoning effort |
|---|---|---|
| **large** | Judge intent from vague evidence, catch what others missed, write the verdict. | high |
| **medium** | Follow a long brief exactly, drive tools for an hour without drifting, notice when the screen disagrees with the plan. | medium to high |
| **small** | Read code fast and report facts with `file:line`. No judgment calls. | medium |

Pick, in order:

1. A model mapping the user or the project already keeps (a settings file naming models per role or tier). Map its roles onto these sizes by what they do.
2. Otherwise the current model family's tiers: its strongest model as **large**, its balanced one as **medium**, its fastest as **small**.
3. When the harness offers one model only, every size is that model; vary effort instead.

The **critic** runs on a large model from a different family than the orchestrator when one is available. Two families disagree in more useful ways than one family twice.

Raise a role one size when its output keeps failing the gate: a tracer whose chains do not connect after one retry, a driver that returns two batches with unrecorded deviations. Record the change in `state.md`. Never lower the orchestrator.

If a requested model fails to launch, say so and use the next size up of the same family, never down.
