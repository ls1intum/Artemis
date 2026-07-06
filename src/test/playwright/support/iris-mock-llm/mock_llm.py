#!/usr/bin/env python3
"""
Mock OpenAI-compatible LLM server for the Iris (real-Pyris) e2e stack.

This is the ONLY mock in the high-fidelity Iris e2e setup: a real Pyris build
(from ls1intum/edutelligence, the Iris microservice) runs unmodified, talks to a
real Weaviate, and calls back to Artemis over the real wire contract. The LLM is
the single thing replaced by this deterministic stub so tests never hit a real
model and stay fast and reproducible.

It implements the small subset of the OpenAI HTTP API that Pyris's
`type: openai_chat` / `openai_embedding` models exercise via the official
openai-python SDK (Chat Completions API, NOT the Responses API; non-streaming):

  POST /v1/chat/completions   -> a canned assistant message (finish_reason "stop",
                                 no tool_calls, with a usage block). If the request
                                 asks for JSON (response_format type=json_object),
                                 a JSON object is returned instead of prose so
                                 Pyris's JsonOutputParser-based sub-pipelines
                                 (e.g. interaction-suggestion `Questions`) parse it.
  POST /v1/embeddings         -> a deterministic constant-dimension embedding vector
                                 (only needed if ingested content triggers retrieval;
                                 harmless to expose regardless).
  GET  /v1/models             -> lists the canned model id.
  GET  /health, GET /         -> 200 liveness.

Why a plain assistant message with no tool_calls: the Pyris course-chat agent uses
LangChain tool-calling. Returning content with NO tool_calls makes the agent loop
terminate in one step and use the content as the final answer, which Pyris then
POSTs back to Artemis as the chat `result`. That rendered text is what the e2e
asserts on, so it must contain the marker substring below.

No third-party dependencies: Python 3 standard library only (so it runs as a bare
process for local iteration and inside a tiny python:slim container in the stack).

Config via environment variables (all optional):
    MOCK_LLM_HOST       bind host                 (default 0.0.0.0)
    MOCK_LLM_PORT       bind port                 (default 8081)
    MOCK_LLM_REPLY      canned chat reply text     (default contains "mock-llm")
    MOCK_LLM_EMBED_DIM  embedding vector dimension (default 1536)
"""

import json
import os
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HOST = os.environ.get("MOCK_LLM_HOST", "0.0.0.0")
PORT = int(os.environ.get("MOCK_LLM_PORT", "8081"))
# The marker substring the e2e test asserts on. Keep "mock-llm" in the default.
CANNED_REPLY = os.environ.get(
    "MOCK_LLM_REPLY",
    "Hello from the mock-llm. This is a deterministic canned answer served to "
    "real Pyris for end-to-end testing of the Artemis Iris integration.",
)
EMBED_DIM = max(0, int(os.environ.get("MOCK_LLM_EMBED_DIM", "1536")))
# When the latest user message contains this marker and the request offers tools,
# the mock answers ONE tool-call round before the canned reply. This lets e2e tests
# exercise the real activity-visibility pipeline (tool start/finish snapshots) with
# a deterministic tool round. The follow-up delay keeps the finished activity chip
# on screen long enough for the UI assertion before the final answer replaces it.
TOOL_MARKER = os.environ.get("MOCK_LLM_TOOL_MARKER", "[e2e-tool]")
TOOL_FOLLOWUP_DELAY_S = float(os.environ.get("MOCK_LLM_TOOL_FOLLOWUP_DELAY_S", "1.5"))
TOOL_PREAMBLE = "Let me check the course details first — mock-preamble"


def log(message: str) -> None:
    print(f"[mock-llm] {message}", flush=True)


def message_text(message: dict) -> str:
    """Extract plain text from a chat message whose content may be a string or parts."""
    content = message.get("content")
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        return " ".join(part.get("text", "") for part in content if isinstance(part, dict))
    return ""


def tool_call_body(model: str, tool_name: str) -> dict:
    """Build a response that asks the agent to run one tool with empty arguments."""
    now = int(time.time())
    return {
        "id": f"chatcmpl-mock-{now}",
        "object": "chat.completion",
        "created": now,
        "model": model or "mock-model",
        "choices": [
            {
                "index": 0,
                "message": {
                    "role": "assistant",
                    "content": TOOL_PREAMBLE,
                    "refusal": None,
                    "tool_calls": [
                        {
                            "id": "call_mock_1",
                            "type": "function",
                            "function": {"name": tool_name, "arguments": "{}"},
                        }
                    ],
                },
                "logprobs": None,
                "finish_reason": "tool_calls",
            }
        ],
        "usage": {"prompt_tokens": 11, "completion_tokens": 9, "total_tokens": 20},
    }


def pick_tool_name(tools: list) -> str:
    """Prefer the no-argument course-details tool; fall back to the first offered tool."""
    names = [t.get("function", {}).get("name", "") for t in tools if isinstance(t, dict)]
    names = [n for n in names if n]
    for preferred in ("get_course_details", "get_simple_course_details"):
        if preferred in names:
            return preferred
    return names[0] if names else "get_course_details"


def chat_completion_body(model: str, wants_json: bool) -> dict:
    """Build a canned OpenAI Chat Completions response object."""
    if wants_json:
        # Several Pyris sub-pipelines request response_format=json_object and parse
        # the content with a JsonOutputParser. The interaction-suggestion pipeline
        # expects a `Questions` object: {"questions": [...]}. A generic object that
        # also carries that key satisfies the common cases; a parse failure there is
        # non-fatal to the chat reply (the answer is already sent first).
        content = json.dumps(
            {
                "questions": [
                    "What is this course about?",
                    "How do I get started?",
                    "Where can I find the lecture materials?",
                ],
                "result": CANNED_REPLY,
                "response": CANNED_REPLY,
            }
        )
    else:
        content = CANNED_REPLY
    now = int(time.time())
    return {
        "id": f"chatcmpl-mock-{now}",
        "object": "chat.completion",
        "created": now,
        "model": model or "mock-model",
        "choices": [
            {
                "index": 0,
                "message": {
                    "role": "assistant",
                    "content": content,
                    "refusal": None,
                    "tool_calls": None,
                },
                "logprobs": None,
                "finish_reason": "stop",
            }
        ],
        "usage": {
            "prompt_tokens": 11,
            "completion_tokens": 17,
            "total_tokens": 28,
        },
    }


def embeddings_body(model: str, inputs: list) -> dict:
    """Build a canned OpenAI embeddings response with constant-dimension vectors."""
    vector = [0.0] * EMBED_DIM
    if EMBED_DIM:
        vector[0] = 1.0  # non-zero so cosine similarity is well-defined
    data = [
        {"object": "embedding", "index": i, "embedding": list(vector)}
        for i in range(max(1, len(inputs)))
    ]
    return {
        "object": "list",
        "data": data,
        "model": model or "mock-embedding",
        "usage": {"prompt_tokens": 1, "total_tokens": 1},
    }


class MockLLMHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def _respond(self, status: int, body: dict) -> None:
        payload = json.dumps(body).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def do_GET(self) -> None:  # noqa: N802 - http.server API
        if self.path.rstrip("/").endswith("/v1/models"):
            self._respond(
                200,
                {
                    "object": "list",
                    "data": [
                        {
                            "id": "mock-model",
                            "object": "model",
                            "created": int(time.time()),
                            "owned_by": "iris-e2e",
                        }
                    ],
                },
            )
            return
        # Liveness for `/`, `/health`, and anything else.
        self._respond(200, {"status": "ok", "mock": True})

    def do_POST(self) -> None:  # noqa: N802 - http.server API
        length = int(self.headers.get("Content-Length", "0") or "0")
        raw = self.rfile.read(length) if length else b""
        try:
            body = json.loads(raw.decode("utf-8")) if raw else {}
        except Exception as error:  # noqa: BLE001 - mock: tolerate any body
            log(f"Could not parse request body: {error}")
            body = {}

        model = body.get("model", "mock-model")
        path = self.path.rstrip("/")

        if path.endswith("/v1/chat/completions"):
            response_format = body.get("response_format") or {}
            wants_json = isinstance(response_format, dict) and response_format.get("type") == "json_object"
            tools = body.get("tools") or []
            messages = body.get("messages") or []
            last_user_text = ""
            for message in reversed(messages):
                if isinstance(message, dict) and message.get("role") == "user":
                    last_user_text = message_text(message)
                    break
            wants_tool_round = bool(tools) and TOOL_MARKER in last_user_text
            has_tool_result = any(isinstance(m, dict) and m.get("role") == "tool" for m in messages)
            log(f"chat.completions model={model!r} json={wants_json} tools={len(tools)} tool_round={wants_tool_round} tool_result={has_tool_result}")
            if wants_tool_round and not has_tool_result:
                self._respond(200, tool_call_body(model, pick_tool_name(tools)))
                return
            if wants_tool_round and has_tool_result and TOOL_FOLLOWUP_DELAY_S > 0:
                # Keep the finished tool chip visible for the UI assertion window.
                time.sleep(TOOL_FOLLOWUP_DELAY_S)
            self._respond(200, chat_completion_body(model, wants_json))
            return

        if path.endswith("/v1/embeddings"):
            inputs = body.get("input")
            if isinstance(inputs, str):
                inputs = [inputs]
            elif not isinstance(inputs, list):
                inputs = [""]
            log(f"embeddings model={model!r} n={len(inputs)}")
            self._respond(200, embeddings_body(model, inputs))
            return

        # Unknown POST: fail fast (404) so endpoint/config drift is visible in this contract-focused stack.
        log(f"unhandled POST {self.path}")
        self._respond(404, {"error": {"message": f"Unknown endpoint: {self.path}", "type": "invalid_request_error"}})

    def log_message(self, format: str, *args) -> None:  # noqa: A002 - argument name must match the BaseHTTPRequestHandler.log_message override
        return


def main() -> int:
    server = ThreadingHTTPServer((HOST, PORT), MockLLMHandler)
    log(f"listening on http://{HOST}:{PORT} (embed_dim={EMBED_DIM})")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        log("shutting down")
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
