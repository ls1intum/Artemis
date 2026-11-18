# Mock OpenAI-compatible LLM for the Iris e2e stack

`mock_llm.py` is a small, dependency-free OpenAI-compatible HTTP server. It is the **only**
mock in the high-fidelity Iris e2e setup (see `../iris-stack/`): a real Pyris build runs
unmodified and the LLM is the single thing replaced, so tests are deterministic and never
hit a real model.

## Endpoints

| Request                    | Response                                                                          |
| -------------------------- | --------------------------------------------------------------------------------- |
| `POST /v1/chat/completions`| Canned assistant message (`finish_reason: "stop"`, no `tool_calls`, with `usage`). Returns JSON content when the request asks for `response_format: {type: json_object}` (e.g. Pyris's interaction-suggestion `Questions` parser). |
| `POST /v1/embeddings`      | Deterministic constant-dimension vector (only hit if ingested content triggers retrieval). |
| `GET /v1/models`           | Lists the canned model id.                                                        |
| `GET /` and `GET /health`  | `200` liveness.                                                                   |

It speaks the **Chat Completions API** (`client.chat.completions.create`), not the Responses
API, and is **non-streaming** — matching what Pyris's `type: openai_chat` model issues. The
canned reply contains the marker substring `mock-llm`, which the e2e test asserts on.

Returning a plain assistant message with **no `tool_calls`** makes Pyris's LangChain
tool-calling agent loop terminate in one step and use the content as the final answer, which
Pyris then posts back to Artemis as the chat `result`.

## Run

In the stack it runs as the `mock-llm` container (`Dockerfile` here). Pyris's
`llm_config.local.yml` points each `openai_chat` model's `base_url` at
`http://mock-llm:8081/v1`.

As a bare process (Python 3 stdlib only):

```bash
MOCK_LLM_PORT=8081 python3 src/test/playwright/support/iris-mock-llm/mock_llm.py
```

Environment variables (all optional):

| Variable             | Default                | Meaning                                |
| -------------------- | ---------------------- | -------------------------------------- |
| `MOCK_LLM_HOST`      | `0.0.0.0`              | bind host                              |
| `MOCK_LLM_PORT`      | `8081`                 | bind port                              |
| `MOCK_LLM_REPLY`     | a fixed sentence with `mock-llm` | canned chat reply text       |
| `MOCK_LLM_EMBED_DIM` | `1536`                 | embedding vector dimension             |
