"""Run-and-collect half of the harness: POST a generation, poll to terminal, collect everything.

Runs go in **rounds**, never in blocks. One round is one replicate of every configuration on both
exercise types; round 2 repeats the same list. That is what makes the matrix interruptible: stopping
after any completed round leaves a balanced corpus with equal n everywhere, and extending is just
running more rounds. It also spreads environment drift evenly across configurations instead of
concentrating it in whichever cells ran late. The order within a round is shuffled so no configuration
always occupies the same position.

Storage is append-only and resumable by ``run_id``: on start the ledger is read, completed ids are
skipped, and new lines are appended. Resuming and extending are therefore the same operation.
"""

import json
import os
import random
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional, Sequence

import requests

import artifacts as artifacts_module
import logs as logs_module
from logging_config import logging
from matrix import CONFIGURATIONS_BY_ID, Configuration, run_id as make_run_id
from utils import CORPUS_PATH, INSTANCE_LOG_GLOB, POLL_SECONDS, SERVER_URL, TIMEOUT_SECONDS, expect

TERMINAL_PHASES = {"COMPLETED", "DRAFT_WITH_WARNINGS", "FAILED", "CANCELLED"}
# Phases that leave a variant exercise behind and therefore have artifacts to capture.
PHASES_WITH_VARIANT = {"COMPLETED", "DRAFT_WITH_WARNINGS"}
# Outcomes that say nothing about the pipeline and must be re-run rather than reported. LOST means the
# server lost the job record (a restart — job records live in Hazelcast, not the database);
# INVALID_ENVIRONMENT means the 429 detector quarantined it. Both are properties of the machine, so
# resume re-queues them instead of treating the run_id as done.
RETRYABLE_PHASES = {"LOST", "INVALID_ENVIRONMENT"}

_ledger_lock = threading.Lock()


def load_corpus() -> Dict[str, Any]:
    with open(CORPUS_PATH, encoding="utf-8") as handle:
        return json.load(handle)


class Harness:
    """Drives runs into one output directory (``results`` for the matrix, ``results-pilot`` for the pilot)."""

    def __init__(self, session: requests.Session, output_dir: str, prompt_commit_sha: str, concurrency: int):
        self.session = session
        self.output_dir = os.path.abspath(output_dir)
        self.prompt_commit_sha = prompt_commit_sha
        self.concurrency = concurrency
        self.corpus = load_corpus()
        for sub in ("raw", "logs", "artifacts"):
            os.makedirs(os.path.join(self.output_dir, sub), exist_ok=True)
        self.ledger_path = os.path.join(self.output_dir, "runs.jsonl")
        # Jobs started but not yet terminal. Killing the client does NOT stop a job — the server keeps
        # generating, so an aborted round silently leaves runs consuming LLM concurrency alongside the next
        # round. That both throttles the provider and invalidates the concurrency the round claims to use.
        self._in_flight: set = set()

    # --- in-flight jobs -----------------------------------------------------------------------------

    def cancel_in_flight(self) -> None:
        """Cancels every job this process started and has not seen finish. Safe to call twice."""
        for job_id in list(self._in_flight):
            try:
                response = self.session.delete(f"{SERVER_URL}/hyperion/variant-jobs/{job_id}")
                # 409 means the job reached a terminal phase first — nothing to cancel, not an error.
                logging.info("cancelled in-flight job %s -> %s", job_id, response.status_code)
            except Exception as error:  # noqa: BLE001 — best effort on the way out
                logging.warning("could not cancel job %s: %s", job_id, error)
            self._in_flight.discard(job_id)

    # --- ledger -------------------------------------------------------------------------------------

    def completed_run_ids(self) -> set:
        """Run ids that need no re-run.

        Last-wins over the append-only ledger, since a retried run appends a second record for the same
        id. Runs whose last record is a retryable environment outcome are deliberately absent, so a resume
        picks them up again.
        """
        if not os.path.exists(self.ledger_path):
            return set()
        last: Dict[str, str] = {}
        with open(self.ledger_path, encoding="utf-8") as handle:
            for line in handle:
                line = line.strip()
                if line:
                    record = json.loads(line)
                    last[record["run_id"]] = record.get("terminal_phase")
        return {run_id for run_id, phase in last.items() if phase not in RETRYABLE_PHASES}

    def _append(self, record: Dict[str, Any]) -> None:
        with _ledger_lock:
            with open(self.ledger_path, "a", encoding="utf-8") as handle:
                handle.write(json.dumps(record, sort_keys=True, default=str) + "\n")

    # --- one run ------------------------------------------------------------------------------------

    def _request_body(self, exercise_type: str, configuration: Configuration) -> Dict[str, Any]:
        group_id = self.corpus["programming_group_id"] if exercise_type == "programming" else self.corpus["quiz_group_id"]
        body: Dict[str, Any] = dict(configuration.request_intents())
        # Placement is identical across all runs — a constant, not a variable.
        body["placement"] = {"type": "EXISTING_GROUP", "existingGroupId": group_id}
        return body

    def _source_exercise_id(self, exercise_type: str) -> int:
        return self.corpus["programming_exercise_id"] if exercise_type == "programming" else self.corpus["quiz_exercise_id"]

    def run_one(self, exercise_type: str, config_id: str, round_number: int, serial: bool = False) -> Dict[str, Any]:
        configuration = CONFIGURATIONS_BY_ID[config_id]
        run_id = make_run_id(exercise_type, config_id, round_number)
        if serial:
            run_id = f"{run_id}-serial"
        body = self._request_body(exercise_type, configuration)
        source_id = self._source_exercise_id(exercise_type)

        started_wall = datetime.now(timezone.utc)
        response = expect(self.session.post(f"{SERVER_URL}/hyperion/exercises/{source_id}/generate-variant", json=body), 200)
        job_id = response.json()["jobId"]
        self._in_flight.add(job_id)
        logging.info("[%s] started job %s", run_id, job_id)

        try:
            detail, terminal_phase = self._poll_to_terminal(run_id, job_id)
        finally:
            self._in_flight.discard(job_id)
        finished_wall = datetime.now(timezone.utc)

        return self._collect(
            run_id=run_id,
            job_id=job_id,
            exercise_type=exercise_type,
            configuration=configuration,
            round_number=round_number,
            serial=serial,
            detail=detail,
            terminal_phase=terminal_phase,
            started_wall=started_wall,
            finished_wall=finished_wall,
        )

    def _poll_to_terminal(self, run_id: str, job_id: str):
        """Polls until terminal or timeout.

        On timeout the job detail is fetched **before** the cancel is issued: cancelling deletes the
        clone and clears the exercise id, so the order matters.
        """
        deadline = time.time() + TIMEOUT_SECONDS
        detail: Optional[Dict[str, Any]] = None
        while time.time() < deadline:
            response = self.session.get(f"{SERVER_URL}/hyperion/variant-jobs/{job_id}")
            if response.status_code == 404:
                # A 24 h TTL expiry or a server restart; either way the run is not reportable.
                return detail, "LOST"
            detail = expect(response, 200).json()
            phase = (detail.get("job") or {}).get("phase")
            if phase in TERMINAL_PHASES:
                return detail, phase
            time.sleep(POLL_SECONDS)

        logging.warning("[%s] hit the %ss timeout; collecting detail before cancelling", run_id, TIMEOUT_SECONDS)
        response = self.session.get(f"{SERVER_URL}/hyperion/variant-jobs/{job_id}")
        if response.status_code == 200:
            detail = response.json()
        cancel = self.session.delete(f"{SERVER_URL}/hyperion/variant-jobs/{job_id}")
        logging.info("[%s] cancel returned %s", run_id, cancel.status_code)
        return detail, "TIMEOUT"

    def _collect(self, **kwargs) -> Dict[str, Any]:
        run_id: str = kwargs["run_id"]
        job_id: str = kwargs["job_id"]
        detail: Optional[Dict[str, Any]] = kwargs["detail"]
        terminal_phase: str = kwargs["terminal_phase"]
        configuration: Configuration = kwargs["configuration"]
        exercise_type: str = kwargs["exercise_type"]

        job = (detail or {}).get("job") or {}

        # Full job detail, unsummarised.
        with open(os.path.join(self.output_dir, "raw", f"{run_id}.json"), "w", encoding="utf-8") as handle:
            json.dump(detail or {}, handle, indent=2, sort_keys=True)

        all_lines = logs_module.read_lines(INSTANCE_LOG_GLOB)
        job_lines = logs_module.slice_for_job(all_lines, job_id)
        with open(os.path.join(self.output_dir, "logs", f"{run_id}.log"), "w", encoding="utf-8") as handle:
            handle.write("\n".join(line.raw for line in job_lines) + ("\n" if job_lines else ""))

        started_at = _parse_instant(job.get("startedAt")) or kwargs["started_wall"]
        finished_at = _parse_instant(job.get("finishedAt")) or kwargs["finished_wall"]
        timeline = logs_module.build_phase_timeline(job_lines, finished_at, started_at)
        telemetry = logs_module.parse_telemetry(job_lines)
        build_waits = logs_module.parse_build_waits(all_lines, started_at, finished_at)

        variant_exercise_id = job.get("variantExerciseId")
        gate: Dict[str, Any] = {"ran": None, "reason": "no variant exercise id"}
        if variant_exercise_id:
            exercise_lines = logs_module.slice_for_exercise(all_lines, int(variant_exercise_id), started_at, finished_at)
            if exercise_type == "programming":
                # Gate 3: the LLM consistency check, the one documented to no-op silently.
                windowed_lines = [line for line in all_lines if started_at <= line.timestamp <= finished_at]
                gate = logs_module.semantic_gate_status(exercise_lines, int(variant_exercise_id), windowed_lines)
            else:
                # The quiz half's gate is the critique soft gate, not the consistency check.
                verifying_outputs = ((detail or {}).get("stepOutputs") or {}).get("VERIFYING") or []
                gate = logs_module.quiz_critique_gate_status(exercise_lines, int(variant_exercise_id), verifying_outputs)

        # Quarantine environment failures BEFORE the outcome is recorded: a rate-limited build resolves
        # nothing and fails both gates, which is indistinguishable from a pipeline failure in the ledger.
        step_output_text = json.dumps((detail or {}).get("stepOutputs") or {})
        windowed_lines = [line for line in all_lines if started_at <= line.timestamp <= finished_at]
        rate_limit = logs_module.rate_limit_status(
            logs_module.slice_for_exercise(all_lines, int(variant_exercise_id), started_at, finished_at) if variant_exercise_id else [],
            int(variant_exercise_id) if variant_exercise_id else None,
            windowed_lines,
            step_output_text,
        )
        recorded_phase = terminal_phase
        if rate_limit["detected"] and terminal_phase not in ("COMPLETED",):
            logging.warning("[%s] Maven Central rate limit detected (%s) — quarantining as INVALID_ENVIRONMENT", run_id, rate_limit["attribution"])
            recorded_phase = "INVALID_ENVIRONMENT"

        artifact_summary: Dict[str, Any] = {}
        if terminal_phase in PHASES_WITH_VARIANT and variant_exercise_id:
            target = os.path.join(self.output_dir, "artifacts", run_id)
            try:
                artifact_summary = artifacts_module.capture(self.session, exercise_type, int(variant_exercise_id), target)
            except Exception as error:  # noqa: BLE001 — an artifact failure must not lose the run's ledger line
                logging.error("[%s] artifact capture failed: %s", run_id, error)
                artifact_summary = {"error": str(error)}

        record = {
            "run_id": run_id,
            "job_id": job_id,
            "exercise_type": exercise_type,
            "config_id": configuration.config_id,
            "round": kwargs["round_number"],
            "serial": kwargs["serial"],
            "target_difficulty": configuration.target_difficulty,
            "domain_key": configuration.domain_key,
            "narrative_style": configuration.narrative_style,
            "placement": "EXISTING_GROUP",
            "terminal_phase": recorded_phase,
            "pipeline_terminal_phase": terminal_phase,
            "rate_limit": rate_limit,
            "failed_in_phase": job.get("failedInPhase"),
            "failure_detail": job.get("failureDetail"),
            "attempts_used": job.get("attempt"),
            "max_attempts": job.get("maxAttempts"),
            "total_tokens_used": job.get("totalTokensUsed"),
            "started_at": started_at.isoformat() if started_at else None,
            "finished_at": finished_at.isoformat() if finished_at else None,
            "wall_seconds": (finished_at - started_at).total_seconds() if started_at and finished_at else None,
            "phase_durations_seconds": timeline.durations,
            "phase_timeline_complete": timeline.complete,
            "phase_timeline_problem": timeline.problem,
            "telemetry": telemetry,
            "build_waits_millis": build_waits,
            "variant_exercise_id": variant_exercise_id,
            "variant_exercise_title": job.get("variantExerciseTitle"),
            "warnings": job.get("warnings"),
            "semantic_gate": gate,
            "concurrency": self.concurrency if not kwargs["serial"] else 1,
            "prompt_commit_sha": self.prompt_commit_sha,
            "log_files": sorted({line.source_file for line in job_lines}),
            "artifact_summary": artifact_summary,
        }
        self._append(record)
        logging.info("[%s] %s in %ss (tokens=%s, gate_ran=%s)", run_id, terminal_phase, record["wall_seconds"], record["total_tokens_used"], gate.get("ran"))
        return record

    # --- rounds -------------------------------------------------------------------------------------

    def run_round(self, round_number: int, config_ids: Sequence[str], exercise_types: Sequence[str], seed: Optional[int] = None) -> List[Dict[str, Any]]:
        """One replicate of every configuration on every exercise type, shuffled, resumable.

        Programming and quiz runs are pooled into the same worker pool; the concurrency limit exists for
        the build agent, and quiz runs do not touch it.
        """
        already_done = self.completed_run_ids()
        queue = [
            (exercise_type, config_id)
            for exercise_type in exercise_types
            for config_id in config_ids
            if make_run_id(exercise_type, config_id, round_number) not in already_done
        ]
        random.Random(seed if seed is not None else round_number).shuffle(queue)
        if not queue:
            logging.info("Round %s already complete; nothing to do", round_number)
            return []

        logging.info("Round %s: %s runs at concurrency %s", round_number, len(queue), self.concurrency)
        records: List[Dict[str, Any]] = []
        try:
            with ThreadPoolExecutor(max_workers=self.concurrency) as pool:
                futures = [pool.submit(self.run_one, exercise_type, config_id, round_number) for exercise_type, config_id in queue]
                for future in futures:
                    try:
                        records.append(future.result())
                    except Exception as error:  # noqa: BLE001 — one bad run must not abort the round
                        logging.error("Run failed with an unhandled error: %s", error)
        except KeyboardInterrupt:
            # Without this the server keeps generating every in-flight run after the client is gone, and the
            # next round then runs against a machine already at concurrency — corrupting its timings.
            logging.warning("interrupted; cancelling in-flight jobs so they do not outlive this process")
            self.cancel_in_flight()
            raise
        return records


    def wait_for_server(self, timeout_seconds: int = 1800) -> bool:
        """Blocks until the server answers, so a restart pauses the matrix instead of failing every run.

        Returns False if it never came back within the timeout, which ends the matrix cleanly rather than
        burning the remaining rounds against a dead server.
        """
        deadline = time.time() + timeout_seconds
        warned = False
        while time.time() < deadline:
            try:
                if self.session.get(f"{SERVER_URL.rsplit('/api', 1)[0]}/management/health", timeout=10).status_code == 200:
                    if warned:
                        logging.info("server is back; continuing")
                    return True
            except requests.RequestException:
                pass
            if not warned:
                logging.warning("server not answering; waiting for it to come back before the next round")
                warned = True
            time.sleep(30)
        logging.error("server did not come back within %ss; stopping", timeout_seconds)
        return False

    def run_matrix(self, rounds: Sequence[int], config_ids: Sequence[str], exercise_types: Sequence[str]) -> None:
        """Runs a list of rounds back to back, unattended.

        Rounds are sequential on purpose: each is a complete replicate of the matrix, so stopping after any
        one of them leaves a balanced corpus. Running them concurrently would break that property and the
        interruptibility that goes with it.
        """
        for index, round_number in enumerate(rounds, start=1):
            if not self.wait_for_server():
                return
            logging.info("=== matrix: round %s (%s of %s) ===", round_number, index, len(rounds))
            try:
                self.run_round(round_number, config_ids, exercise_types)
            except KeyboardInterrupt:
                logging.warning("matrix interrupted during round %s; resume with the same command", round_number)
                return
            except Exception as error:  # noqa: BLE001 — one bad round must not lose the rounds after it
                logging.error("round %s aborted with an unhandled error: %s", round_number, error)
        logging.info("=== matrix finished: rounds %s ===", list(rounds))


def _parse_instant(value: Optional[str]) -> Optional[datetime]:
    if not value:
        return None
    return datetime.fromisoformat(value.replace("Z", "+00:00"))