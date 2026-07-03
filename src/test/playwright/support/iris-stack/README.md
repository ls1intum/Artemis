# Iris (real-Pyris) e2e stack

This brings up a **real Pyris** (the Iris LLM microservice from
[`ls1intum/edutelligence`](https://github.com/ls1intum/edutelligence), subdir `iris/`)
wired into the Artemis e2e setup, with **only the LLM mocked**. It exercises the genuine
Artemis ↔ Pyris wire contract (pipeline run, async status callbacks, health) end to end,
without ever contacting a real model.

```text
 Artemis (host :8080) ──POST /api/v1/pipelines/chat/run──▶ Pyris (container :8000)
        ▲                                                      │  uses mock LLM
        │  POST /api/iris/internal/pipelines/chat/runs/        │  (openai_chat base_url
        │       {token}/status   (Bearer {token})              ▼   = http://mock-llm:8081/v1)
        └──────────────────────────────────────────────  mock-llm (container :8081)
                                                          Weaviate (container :8001/:50051)
```

Components (see `docker-compose.yml`):

| Service     | Image                                             | Purpose                                                            |
| ----------- | ------------------------------------------------- | ------------------------------------------------------------------ |
| `pyris-app` | `pyris-e2e:local` (built, see below)              | Real Pyris. Reads the mounted config; calls back to Artemis on the host. |
| `weaviate`  | `cr.weaviate.io/semitechnologies/weaviate:1.34.10`| Vector DB. Required for Pyris `/health` to be UP and for chat to run. |
| `mock-llm`  | `iris-mock-llm:local` (built from `../iris-mock-llm`) | Deterministic OpenAI-compatible LLM. Canned reply contains `mock-llm`. |

## Why this is the high-fidelity version

The earlier stub approach faked *Pyris itself*. This stack runs the **real Pyris** and
fakes only the LLM. That means the test verifies the actual contract: endpoint paths,
the `ChatPipelineExecutionDTO` ⇄ `PyrisChatPipelineExecutionDTO` field mapping, the async
202-then-callback flow, the `Authorization: Bearer {token}` callback auth, the stage state
machine, and Pyris's health gating — all against the genuine Pyris code.

## Version compatibility (important)

The unified chat pipeline (`POST /api/v1/pipelines/chat/run` + the
`/api/iris/internal/pipelines/chat/runs/{token}/status` callback) on the Artemis side
matches edutelligence **`origin/main`**, which introduced the unified `chat` endpoint in
commit `82185b1f` (PR #434, "Merge chattype-specific pipelines into a single chat
pipeline"). Older Pyris checkouts (and the stale local `iris/video-transcription-pipeline`
branch) still use the *split* `course-chat`/`lecture-chat` endpoints and are **not**
compatible with this Artemis branch. Build from `origin/main` (or a commit that contains
`82185b1f`).

This stack was validated against edutelligence `origin/main` at commit `f63e2337`.

## 1. Build the Pyris image

Pyris's Dockerfile (`iris/Dockerfile`) `COPY memiris ../memiris`, so the **build context
must be the edutelligence repo root** (the directory that contains both `iris/` and
`memiris/`), not `iris/`:

```bash
# from a fresh checkout of edutelligence at origin/main:
git clone https://github.com/ls1intum/edutelligence.git
cd edutelligence
git checkout origin/main          # or a commit containing 82185b1f
docker build -f iris/Dockerfile -t pyris-e2e:local .
```

Override the tag the stack uses with `PYRIS_IMAGE` if you build it under another name.

## 2. Configuration

Two files in this directory are mounted into the Pyris container:

- `application.local.yml` → `/config/application.yml`
- `llm_config.local.yml`  → `/config/llm_config.yml`

Key choices, and why:

- `api_keys[0].token: "iris-e2e-secret-token"` — the shared secret. It **must equal**
  Artemis `artemis.iris.secret-token` (the runner sets `ARTEMIS_IRIS_SECRETTOKEN` to the
  same value). Pyris raw-compares the incoming `Authorization` header to this token.
- `weaviate.host: "weaviate"` — the compose service name (same docker network).
- `memiris.enabled: false` — disables memory pipelines so no Ollama/embedding hosts are
  contacted at message time.
- `local_llm_enabled: false` — only the `.cloud` LLM ids must resolve; Artemis sends
  `selection=CLOUD_AI` by default, so only cloud ids are used.
- `llm_configuration` includes the `chat_pipeline` key (the unified pipeline, PIPELINE_ID
  `chat_pipeline`). Every `chat`/`keyword_summary` cloud id resolves to a mock-backed
  `openai_chat` model. Embedding/reranker ids must *exist* (referenced by the retrieval
  dependency pipelines) but are never invoked on an empty course with no ingested content.
- In `llm_config.local.yml`, each `type: openai_chat` model sets `base_url:
  http://mock-llm:8081/v1`, redirecting the OpenAI client to the mock.

## 3. Start / stop the stack manually

```bash
# from the Artemis repo root
docker compose -f src/test/playwright/support/iris-stack/docker-compose.yml up -d

# verify Pyris is healthy (Weaviate UP + chat pipeline valid). Must send the token:
curl -s -H "Authorization: iris-e2e-secret-token" http://localhost:8000/api/v1/health/
# -> {"isHealthy":true, ...}

docker compose -f src/test/playwright/support/iris-stack/docker-compose.yml down -v
```

The mock LLM is published on host port `8091` by default (override `MOCK_LLM_HOST_PORT`)
purely for debugging; Pyris reaches it over the docker network regardless.

> Note: `isHealthy` is `true` even though the `Pipelines` module reports `DEGRADED`
> ("rewriting has no default variant"). Only the `chat` pipeline is CRITICAL for health,
> and it is valid, so the overall status is healthy — which is all Artemis requires.

## 4. Run the e2e tests with Iris enabled

The runner brings up this stack and enables Iris on the Artemis server when `RUN_IRIS=true`:

```bash
RUN_IRIS=true ./run-e2e-tests-local-fast.sh --skip-db --filter "Iris"
```

What `RUN_IRIS=true` does (see `run-e2e-tests-local-fast.sh`):

1. Verifies the `pyris-e2e:local` image exists and brings up this stack, waiting for
   Pyris `/api/v1/health/` to report `isHealthy:true`.
2. Exports the Artemis server config that turns Iris on:
   - `ARTEMIS_IRIS_ENABLED=true` — the single switch; also adds `iris` to
     `management/info` `activeModuleFeatures`, which gates the Iris UI client-side. (There
     is no Spring `iris` profile; enabling is purely this property.)
   - `ARTEMIS_IRIS_URL=http://localhost:8000` — Artemis → Pyris.
   - `ARTEMIS_IRIS_SECRETTOKEN=iris-e2e-secret-token` — must match `api_keys[0].token`.
3. Overrides `server.url` to `http://host.docker.internal:8080` so that the
   `artemisBaseUrl` Artemis hands to Pyris is reachable from inside the container (Pyris
   posts status callbacks there). The Playwright browser uses `BASE_URL`
   (`http://localhost:9000`) independently, and the Iris suite only touches lecture/course
   flows, so this override is safe for the Iris-filtered run.

Course-level Iris settings default to `enabled` for the seed lecture course (9022), and the
spec additionally PUTs `api/iris/courses/9022/iris-settings {enabled:true}` defensively.

`./run-e2e-tests-local-fast.sh --stop` also tears this stack down.

## Networking summary

- **Artemis → Pyris**: `http://localhost:8000` (Pyris host-published port).
- **Pyris → Artemis** (callback): `http://host.docker.internal:8080` (the host; resolves
  inside the container via the `host-gateway` extra_host).
- **Pyris → LLM**: `http://mock-llm:8081/v1` (docker network).
- **Pyris → Weaviate**: `weaviate:8001` (REST) + `weaviate:50051` (gRPC) (docker network).
