# Exercise-variant generation evaluation

Measures how often the AI exercise-variant pipeline works, what it costs, and how good the generated
exercises are. Structure mirrors `supporting_scripts/hyperion/consistency-check-benchmark`.

## Layout

| Path | What it holds |
|---|---|
| `corpus/` | The two source exercises and the scripts that create them, plus `corpus.json` (the ids every later stage reads) and `sources/` (snapshots the report can quote without a live instance). |
| `matrix.py` | The two fixed domain texts with their rationale, and the fourteen configurations. |
| `runner.py` | Run-and-collect: POST, poll, collect raw detail, log slice, and artifacts. Rounds, concurrency, resume. |
| `logs.py` | Instance-log slicing, phase-timeline reconstruction, semantic-gate detection. |
| `artifacts.py` | Per-variant artifact capture through the REST API (no git clones). |
| `checks.py` | The three automated checks over stored artifacts. |
| `analysis.py` | Tables and figures. |
| `rubric.md` | The rubric and its anchors, frozen before the first score. |
| `run_evaluation.py` | CLI driver — the same entry points the notebook calls. |
| `results/` | The measured matrix. `results-pilot/` holds the pre-freeze pilot, kept as evidence. |

## Setup

```bash
python3 -m venv venv
./venv/bin/pip install -r requirements.txt
cp config.ini.example config.ini    # then fill in credentials — config.ini is untracked
```

The instance under test is started by `local/eval/start-server.sh` (untracked): MySQL, local VC, local
CI, Hyperion pointed at Logos. It writes an **unrotated** log per start, so no run can be discarded
before the harness slices it, and `logs.py` globs `instance-*.log` rather than following the symlink the
script repoints on each restart.

### Build image: Maven Central rate limiting (required, not optional)

Build containers are ephemeral and share no `.m2`, so **every** build re-resolves every artifact from
Maven Central. At the request volume this matrix produces, Central answers with HTTP 429. That surfaces as
an ordinary build failure, so left alone it silently corrupts the reliability rates this evaluation exists
to measure — one pilot round came back 0/5 for this reason alone and was initially misread as a prompt
regression.

Before running anything, build the mirrored image and retag it over the tag exercises reference:

```bash
docker build -t artemis-maven-warm:local local/eval/warm-image
docker tag ls1tum/artemis-maven-template:java17-25 ls1tum/artemis-maven-template:java17-25-upstream
docker tag artemis-maven-warm:local ls1tum/artemis-maven-template:java17-25
```

The image is the stock one plus a `settings.xml` mirroring Central to Google's copy, which is unthrottled.
Undo with `docker pull ls1tum/artemis-maven-template:java17-25`.

**Why retagging rather than configuration.** `dockerImage` is baked into each exercise's
`build_plan_configuration` when the exercise is created, so
`artemis.continuous-integration.build.images.java.default` only affects exercises created *after* the
change — and variants inherit the source exercise's value regardless. Retagging is the only mechanism that
reaches exercises that already exist. (The environment-variable form of that property does not work at all:
`images` binds as a nested `Map`, and Spring takes map keys from an environment variable literally, so
`ARTEMIS_..._IMAGES_JAVA_DEFAULT` yields the key `JAVA`, never `java`.)

Verify with `docker inspect` on a running `local-ci-*` container: its image id must equal
`docker image inspect artemis-maven-warm:local --format '{{.Id}}'`. `logs.py:rate_limit_status` detects any
429 that slips through and `runner.py` quarantines the run as `INVALID_ENVIRONMENT`, excluded from every
rate and re-queued on the next resume — but detection is a safety net, not a substitute for the mirror.

Set `concurrent-build-size` to at least `2 x concurrency` in `application-local.yml` (6 for the default
concurrency of 3). At the stock value of 2 — exactly one exercise's solution+template pair — the build
queue serialises and inflates every measured phase duration with queue wait.

## Running

```bash
./venv/bin/python corpus/create_corpus.py                                   # once
./venv/bin/python run_evaluation.py one --type quiz --config C3 --round 0 --out results-pilot
./venv/bin/python run_evaluation.py round --round 1 --out results --concurrency 3
./venv/bin/python run_evaluation.py attach --job-id <uuid> --type quiz --config C3 --round 0 --out results-pilot
```

The whole measured matrix, unattended — this is the intended way to run Stage 2:

```bash
./venv/bin/python run_evaluation.py matrix --rounds 1-6 --out results --concurrency 3
```

Re-running the identical command after any interruption resumes it: completed `run_id`s are skipped, and
runs that ended as `LOST` (a server restart — job records live in Hazelcast, not the database) or
`INVALID_ENVIRONMENT` are re-queued rather than reported. Between rounds it waits for the server to come
back rather than failing every remaining run against a dead one.

**Do not restart the server while a round is running.** In-flight jobs are lost with it. They are re-queued
on resume, so the cost is those runs' wall time, not their data.

Check the Stage 1 freeze gate on a pilot round with:

```bash
./venv/bin/python freeze_gate.py --round 8 --out results-pilot --configs C1 C2 C3 C4 C13
```

Runs are long; start them detached. The ledger (`runs.jsonl`) is append-only and resumable by `run_id`,
so an interrupted client never loses a finished run, and resuming and extending are the same operation:
run more rounds.

**Rounds, not blocks.** One round is one replicate of every configuration on both exercise types.
Stopping after any completed round leaves a balanced corpus with equal n everywhere. Because job records
have a 24-hour TTL, a round cannot be paused overnight and backfilled — run rounds in contiguous blocks.

## Frozen prompt version

The measured runs use the prompts at commit `f8bbc1badc5ab5ba33099c0ce47284ea0d703959` in
`src/main/resources/prompts/hyperion/variants/`. Nothing there changes after the freeze; a prompt defect
surfacing during the measured runs is a finding for the report, not a reason to edit, because fixing it
would invalidate every earlier run.

Every line in `runs.jsonl` records the `prompt_commit_sha` it ran under, so a version mismatch is
detectable in the data rather than assumed away.

## Deviations from stock Artemis

The measured pipeline differs from the base branch by two committed changes, both made before the
freeze and both recorded in the report's setup section:

1. **A phase-timeline log line** in `ExerciseVariantJobService.updatePhase` — observation only.
2. **A short-answer mapping reconnection fix** in `QuizVariantTools`. Without it every quiz variant whose
   agent touched a short-answer question failed to save (`Detached entity passed to persist`), so the
   quiz half would have measured that one defect rather than the pipeline. `DragAndDropQuestion` has the
   same latent defect in its `correctMappings` and was deliberately left alone: drag-and-drop is rejected
   server-side and out of scope here.
