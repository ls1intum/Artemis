#!/usr/bin/env python3
"""
Deterministic OpenAI-compatible mock LLM for the Hyperion exercise-variant E2E stack.

Unlike the Iris stack (real Pyris in front of a dumb canned LLM), Hyperion talks to
the LLM directly through Spring AI's OpenAI ChatClient. So this mock has to be
*variant-flow aware*: it inspects each request and returns exactly what the
generation pipeline expects at that phase, so a quiz variant runs to COMPLETED with
no real model and no randomness.

The pipeline issues these Spring-AI calls (see ExerciseVariantGenerationPipeline,
QuizVariantAdapters, VariantAgentLoopRunner); the mock classifies them the same way
the server integration test's scripted ChatModel does:

  PLANNING    user message contains "Produce the change plan"
              -> a valid ChangePlan JSON (BeanOutputConverter parses the content).
  TRANSFORMING the request carries `tools` (the quiz toolset)
              -> a single `finish` tool call. The provisioned clone is already a
                 valid copy of the source quiz, so finishing without edits yields a
                 valid variant; verification (isValid + file check + critique) passes.
  VERIFYING   user message contains "Review the variant quiz" (quiz critique gate)
              -> {"findings": []} so the soft gate reports no problems.
  FAILURE     user message contains "Write the summary for the instructor"
              -> a canned post-mortem (only reached on failure paths).

The variant title is deterministic (VARIANT_TITLE) so the E2E can assert the exact
exercise surfaces in the course list.

The mock serves blocking Responses requests. Python 3 standard library only — runs as a bare
process (see supporting_scripts/e2e/run-e2e-tests-local-fast.sh, RUN_HYPERION).

Config via environment variables (all optional):
    MOCK_LLM_HOST          bind host        (default 127.0.0.1)
    MOCK_LLM_PORT          bind port        (default 8090)
    MOCK_LLM_VARIANT_TITLE planned title    (default "AI Variant - Cargo Bay Inventory")
"""

import json
import os
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

# Loopback by default: the mock is unauthenticated. Set MOCK_LLM_HOST=0.0.0.0 only if reached from outside.
HOST = os.environ.get("MOCK_LLM_HOST", "127.0.0.1")
PORT = int(os.environ.get("MOCK_LLM_PORT", "8090"))
# Deterministic planned title the E2E asserts on. Kept free of characters that a
# short-name deriver would choke on; PROVISIONING derives the short name from it.
VARIANT_TITLE = os.environ.get("MOCK_LLM_VARIANT_TITLE", "AI Variant - Cargo Bay Inventory")

# When a request carries this marker, the PLANNING response is delayed by SLOW_DELAY_S so the
# E2E can observe the job while it is still running (wizard closable mid-run, tray spinner).
SLOW_MARKER = os.environ.get("MOCK_LLM_SLOW_MARKER", "[slow-e2e]")
SLOW_DELAY_S = float(os.environ.get("MOCK_LLM_SLOW_DELAY_S", "6"))

# The ChangePlan the planner "produces". Field names must match the ChangePlan record
# so Spring AI's BeanOutputConverter deserializes it (variantTitle, problemStatement,
# intendedChanges, invariants).
CHANGE_PLAN = {
    "variantTitle": VARIANT_TITLE,
    "problemStatement": "Check the cargo bay inventory of the orbital station.",
    "intendedChanges": ["Re-theme the questions from the generic domain to a space-station cargo bay"],
    "invariants": ["Keep the scoring type and point value of every question unchanged"],
}


def log(message: str) -> None:
    print(f"[hyperion-mock-llm] {message}", flush=True)


def message_text(message: dict) -> str:
    """Extract plain text from a chat message whose content may be a string or parts."""
    content = message.get("content")
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        return " ".join(part.get("text", "") for part in content if isinstance(part, dict))
    return ""


def all_text(messages: list) -> str:
    """Concatenate every message's text — the phase markers may sit in the system or user turn."""
    return "\n".join(message_text(m) for m in messages if isinstance(m, dict))


def responses_body(model: str, output: list) -> dict:
    return {
        "id": f"resp-mock-{time.time_ns()}",
        "object": "response",
        "created_at": int(time.time()),
        "model": model or "mock-model",
        "status": "completed",
        "output": output,
        "usage": {"input_tokens": 100, "output_tokens": 20, "total_tokens": 120},
    }


def content_response(model: str, content: str) -> dict:
    return responses_body(model, [{
        "type": "message",
        "id": f"msg-mock-{time.time_ns()}",
        "role": "assistant",
        "status": "completed",
        "content": [{"type": "output_text", "text": content, "annotations": []}],
    }])


def finish_tool_response(model: str, tools: list) -> dict:
    """Ask the agent to call finish. The provisioned clone is already valid."""
    tool_names = [tool["name"] for tool in tools if isinstance(tool, dict) and tool.get("name")]
    tool_name = "finish" if "finish" in tool_names or not tool_names else tool_names[0]
    return responses_body(model, [{
        "id": "fc_mock_finish",
        "call_id": "call_mock_finish",
        "type": "function_call",
        "status": "completed",
        "name": tool_name,
        "arguments": json.dumps({"summary": "Re-themed the quiz to the cargo bay domain and validated it."}),
    }])


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
            self._respond(200, {"object": "list", "data": [{"id": "mock-model", "object": "model", "created": int(time.time()), "owned_by": "hyperion-e2e"}]})
            return
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

        if not path.endswith("/v1/responses"):
            log(f"unhandled POST {self.path}")
            self._respond(404, {"error": {"message": f"Unknown endpoint: {self.path}", "type": "invalid_request_error"}})
            return

        tools = body.get("tools") or []
        messages = body.get("input") or []
        text = messages if isinstance(messages, str) else all_text(messages)

        # Deterministic slow path: when the request carries SLOW_MARKER (the E2E puts it in the
        # domain text, which lands in the planning system prompt), hold the PLANNING response so
        # the job stays visibly "generating" long enough for the wizard-close / tray assertions.
        if SLOW_MARKER in text and "Produce the change plan" in text:
            log(f"slow marker present — delaying PLANNING response by {SLOW_DELAY_S}s")
            time.sleep(SLOW_DELAY_S)

        if tools:
            phase, response = "TRANSFORMING(tool)", finish_tool_response(model, tools)
        elif "Produce the change plan" in text:
            phase, response = "PLANNING", content_response(model, json.dumps(CHANGE_PLAN))
        elif "Review the variant quiz" in text:
            phase, response = "VERIFYING(critique)", content_response(model, json.dumps({"findings": []}))
        elif "Write the summary for the instructor" in text:
            phase, response = "FAILURE_SUMMARY", content_response(model, "The source exercise is untouched; retry with a simpler request.")
        else:
            phase, response = "OTHER", content_response(model, "ok")

        log(f"responses phase={phase} tools={len(tools)}")
        self._respond(200, response)

    def log_request(self, code="-", size="-") -> None:
        """Silence the per-request access log. Overriding this instead of `log_message` keeps the base signature
        verbatim — `log_message` names its first parameter after the `format` builtin, and either shadowing or
        renaming it is a static-analysis finding. Error logging stays on, which is what a failing run needs."""
        return


def main() -> int:
    server = ThreadingHTTPServer((HOST, PORT), MockLLMHandler)
    log(f"listening on http://{HOST}:{PORT} (variant title: {VARIANT_TITLE!r})")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        log("shutting down")
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
