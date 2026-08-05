"""Instance-log slicing and phase-timeline reconstruction.

All timings come from the server log, never from polling — polling only notices that a job finished.
The phase timeline is derived from the one INFO line added in ``ExerciseVariantJobService.updatePhase``
("Variant job {id} entering phase {PHASE}"), so every phase duration falls out by subtraction.

Log files are located by glob, not through the ``instance.log`` symlink: ``start-server.sh`` repoints
that symlink on every restart, so following it would read a file that is no longer live, and a run whose
lines straddle a restart would silently lose half of them. Each slice records which files it came from.
"""

import glob
import os
import re
import threading
from datetime import datetime
from typing import Dict, List, NamedTuple, Optional, Tuple

ANSI_PATTERN = re.compile(r"\x1b\[[0-9;]*m")

# 2026-08-05T10:05:47.982+02:00  INFO 55202 --- [Artemis] [ocal-ci-build-2] logger.Name : message
LINE_PATTERN = re.compile(
    r"^(?P<timestamp>\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}[+-]\d{2}:\d{2})\s+"
    r"(?P<level>[A-Z]+)\s+\d+\s+---\s+.*?:\s(?P<message>.*)$"
)

PHASE_PATTERN = re.compile(r"Variant job (?P<job_id>\S+) entering phase (?P<phase>[A-Z_]+)")
TELEMETRY_PATTERN = re.compile(r"Variant job (?P<job_id>\S+) telemetry — tool calls: \[(?P<tools>[^\]]*)\], builds: \[(?P<builds>[^\]]*)\]")
STAT_PATTERN = re.compile(r"(?P<label>[\w.-]+)=(?P<count>\d+)x/(?P<millis>\d+)ms")
BUILD_WAIT_PATTERN = re.compile(r"Found build result for commit (?P<commit>\w+) after (?P<polls>\d+) polls \((?P<millis>\d+)ms\)")
# Timed-out builds are the longest waits and are logged on WARN paths with a different wording. Excluding
# them would bias the build-wait distribution low exactly where timing matters most. Note the job record's
# own telemetry (recordBuildStat) measures trigger-to-return and therefore already includes timeouts, so
# the two sources agree only once these are counted here as well.
BUILD_TIMEOUT_PATTERNS = (
    re.compile(r"Timed out waiting for build result for commit (?P<commit>\w+) in exercise (?P<exercise>\d+) after (?P<polls>\d+) polls \((?P<millis>\d+)ms\)"),
    re.compile(r"Timed out waiting for (?P<repository>\w+) build result in exercise (?P<exercise>\d+) after (?P<millis>\d+)ms"),
)

# --- Environment failures ------------------------------------------------------------------------------
# Maven Central rate-limits unauthenticated clients with HTTP 429. When it does, every build resolves
# nothing, runs 0 tests, and both build gates fail — so the run looks exactly like a pipeline failure and
# would be counted as one. It is not: it measures the network. Runs where this fires are quarantined as
# INVALID_ENVIRONMENT rather than recorded as an outcome.
#
# Two independent detection surfaces, because either alone can miss:
#  * Artemis's own MavenCentralRateLimitNotificationService logs a line naming the exercise — exact
#    attribution, but only fires on the paths that service watches.
#  * The build log captured in the job's VERIFYING step outputs contains the raw Maven error — scoped to
#    the run by construction, and catches cases the service does not classify.
RATE_LIMIT_SERVICE_LINE = "rate limited dependency downloads"
RATE_LIMIT_EXERCISE_LINE = "A build of programming exercise {exercise_id} failed because Maven Central"
RATE_LIMIT_BUILD_LOG_MARKERS = ("status code: 429", "Too Many Requests")


def rate_limit_status(exercise_lines: List[LogLine], exercise_id: Optional[int], windowed_lines: List[LogLine], step_output_text: str = "") -> Dict[str, object]:
    """Whether this run's builds were rate-limited by Maven Central.

    ``exercise_id`` may be None for runs that never provisioned a variant; detection then falls back to the
    time window, which is approximate under concurrency — deliberately biased towards false positives, since
    wrongly quarantining a run costs a re-run while wrongly counting one corrupts a reliability rate.
    """
    exercise_scoped = 0
    if exercise_id is not None:
        needle = RATE_LIMIT_EXERCISE_LINE.format(exercise_id=exercise_id)
        exercise_scoped = sum(1 for line in exercise_lines if needle in line.raw)
    windowed = sum(1 for line in windowed_lines if RATE_LIMIT_SERVICE_LINE in line.raw)
    in_build_log = sum(step_output_text.count(marker) for marker in RATE_LIMIT_BUILD_LOG_MARKERS)
    return {
        "exercise_scoped_hits": exercise_scoped,
        "windowed_hits": windowed,
        "build_log_hits": in_build_log,
        "attribution": "exact" if exercise_scoped or in_build_log else ("time-windowed" if windowed else "none"),
        "detected": bool(exercise_scoped or in_build_log or windowed),
    }


# --- Quiz semantic gate (the LLM self-critique) -------------------------------------------------------
# The quiz half's semantic gate is NOT the consistency check — it is the critique soft gate in
# QuizVariantAdapters. It logs only its two skip paths and has no positive "ran" line, so it is detected by
# the absence of a skip, corroborated by findings when the critique actually reported something.
QUIZ_CRITIQUE_SKIPPED_UNCONFIGURED = "Skipping quiz variant critique for exercise {exercise_id}"
QUIZ_CRITIQUE_FAILED = "Quiz variant critique failed for exercise {exercise_id}"
QUIZ_CRITIQUE_FINDING_MARKER = "[QUIZ_CRITIQUE]"

# --- Programming semantic gate (the consistency check) ------------------------------------------------
# The start/complete lines carry the *variant exercise* id, so they are matched against the exercise
# slice. The degradation line does not — see semantic_gate_status.
GATE_STARTED = "Performing consistency check for exercise {exercise_id}"
GATE_COMPLETED_PATTERN = "Consistency check for exercise {exercise_id} complete"
# Covers all three skip paths: the checkConsistency catch, and the await timeout / execution failure.
GATE_SKIPPED = "Skipping the semantic gate"
# The provider-refused path returns an empty list instead of raising, so the gate "runs" and finds nothing.
# This line carries the prompt resource path, NOT the exercise id, so it can only be matched over the
# run's time window — which is approximate under concurrency, but a noisy signal beats one that is
# structurally always negative.
GATE_DEGRADED = "Failed to obtain or parse AI response"


class LogLine(NamedTuple):
    timestamp: datetime
    level: str
    message: str
    raw: str
    source_file: str


def _strip_ansi(text: str) -> str:
    return ANSI_PATTERN.sub("", text)


def log_files(pattern: str) -> List[str]:
    """Every instance log matching the glob, oldest first."""
    return sorted(glob.glob(pattern), key=os.path.getmtime)


# Parsed lines per file, with the byte offset already consumed. Every finished run slices the log, so a
# naive re-read would re-parse the whole (growing, hundreds-of-MB) instance log once per run — quadratic
# across a full matrix. Only bytes appended since the last call are parsed. Guarded because runs collect
# concurrently.
_cache_lock = threading.Lock()
_parsed_cache: Dict[str, Tuple[int, List["LogLine"]]] = {}


def _parse_file(path: str) -> List["LogLine"]:
    with _cache_lock:
        offset, cached = _parsed_cache.get(path, (0, []))
        size = os.path.getsize(path)
        if size < offset:
            # The file shrank: not an append, so the cache cannot be trusted. Re-parse from the start.
            offset, cached = 0, []
        if size == offset:
            return list(cached)

        lines = list(cached)
        with open(path, encoding="utf-8", errors="replace") as handle:
            handle.seek(offset)
            for raw in handle:
                clean = _strip_ansi(raw.rstrip("\n"))
                match = LINE_PATTERN.match(clean)
                if not match:
                    continue
                lines.append(
                    LogLine(
                        timestamp=datetime.fromisoformat(match.group("timestamp")),
                        level=match.group("level"),
                        message=match.group("message"),
                        raw=clean,
                        source_file=os.path.basename(path),
                    )
                )
            consumed = handle.tell()
        _parsed_cache[path] = (consumed, lines)
        return list(lines)


def read_lines(pattern: str) -> List[LogLine]:
    """Parses every matching log file into structured lines, skipping continuation lines (stack traces)."""
    lines: List[LogLine] = []
    for path in log_files(pattern):
        lines.extend(_parse_file(path))
    return lines


def slice_for_job(lines: List[LogLine], job_id: str) -> List[LogLine]:
    """Every log line carrying this job id."""
    return [line for line in lines if job_id in line.raw]


def slice_for_exercise(lines: List[LogLine], exercise_id: int, start: datetime, end: datetime) -> List[LogLine]:
    """Lines mentioning a variant exercise id within the run's window — used for the semantic gate."""
    needle = f"exercise {exercise_id}"
    return [line for line in lines if start <= line.timestamp <= end and needle in line.raw]


class PhaseTimeline(NamedTuple):
    entries: List[Dict[str, object]]
    durations: Dict[str, float]
    complete: bool
    problem: Optional[str]


def build_phase_timeline(job_lines: List[LogLine], finished_at: Optional[datetime], started_at: Optional[datetime] = None) -> PhaseTimeline:
    """Phase entry timestamps and per-phase durations by subtraction.

    A phase visited several times (the verify/repair loop) accumulates its durations. The last phase is
    closed by ``finished_at`` from the job record, because terminal transitions do not go through
    ``updatePhase``. Fails loudly rather than dropping an incomplete timeline quietly.

    The leading gap is named QUEUED rather than discarded. ``startedAt`` is stamped in ``startJob`` on the
    REST thread, while the first phase line is emitted once ``hyperionVariantTaskExecutor`` picks the job
    up; the difference is queue wait. Without it the durations cannot reconcile with
    ``finished_at - started_at`` and every run looks like a parser bug. It is also real elapsed time an
    instructor waits through, and a useful invariant: the executor is core 4 / max 8 / queue 32, so at
    harness concurrency 3 nothing should queue. A large QUEUED means the harness is submitting faster
    than the pool drains and the wall-time numbers are contaminated.
    """
    entries: List[Dict[str, object]] = []
    for line in job_lines:
        match = PHASE_PATTERN.search(line.message)
        if match:
            entries.append({"phase": match.group("phase"), "at": line.timestamp})

    if not entries:
        return PhaseTimeline([], {}, False, "no phase-transition lines found for this job")

    durations: Dict[str, float] = {}
    if started_at is not None:
        queued_seconds = (entries[0]["at"] - started_at).total_seconds()  # type: ignore[operator]
        if queued_seconds < 0:
            return PhaseTimeline(entries, durations, False, "first phase line precedes startedAt")
        durations["QUEUED"] = queued_seconds
    for index, entry in enumerate(entries):
        if index + 1 < len(entries):
            end = entries[index + 1]["at"]
        elif finished_at is not None:
            end = finished_at
        else:
            return PhaseTimeline(entries, durations, False, "job has no finishedAt to close the final phase")
        seconds = (end - entry["at"]).total_seconds()  # type: ignore[operator]
        if seconds < 0:
            return PhaseTimeline(entries, durations, False, f"negative duration for phase {entry['phase']}")
        durations[str(entry["phase"])] = durations.get(str(entry["phase"]), 0.0) + seconds

    return PhaseTimeline(entries, durations, True, None)


def parse_telemetry(job_lines: List[LogLine]) -> Dict[str, Dict[str, Dict[str, int]]]:
    """Per-tool-call and per-build totals from the terminal telemetry summary line."""
    result: Dict[str, Dict[str, Dict[str, int]]] = {"tool_calls": {}, "builds": {}}
    for line in job_lines:
        match = TELEMETRY_PATTERN.search(line.message)
        if not match:
            continue
        for key, group in (("tool_calls", "tools"), ("builds", "builds")):
            for stat in STAT_PATTERN.finditer(match.group(group)):
                result[key][stat.group("label")] = {"count": int(stat.group("count")), "total_millis": int(stat.group("millis"))}
    return result


def parse_build_waits(lines: List[LogLine], start: datetime, end: datetime) -> List[Dict[str, object]]:
    """Individual build waits inside the run's window, successful and timed out alike.

    **Usually empty for programming runs, and that does not mean no builds ran.** The pipeline verifies
    template and solution with the *joint* wait (``waitForBuildResults``), whose success path logs nothing
    at all — only its warnings are logged. The single-build path that does log "Found build result …" is
    not the one in use. Build timing therefore comes from the job record's own ``builds:`` telemetry
    (e.g. ``VERIFYING:TEMPLATE+SOLUTION (joint)=5x/82568ms``), which measures trigger-to-return and
    includes timeouts by construction; this parser stays as a second source for the single-build path and
    for the timeout warnings, which the joint path does log.

    Timeouts are included and flagged: they are the longest waits, and dropping them would bias the
    distribution low exactly where timing matters.
    """
    waits: List[Dict[str, object]] = []
    for line in lines:
        if not (start <= line.timestamp <= end):
            continue
        match = BUILD_WAIT_PATTERN.search(line.message)
        if match:
            waits.append({"millis": int(match.group("millis")), "timed_out": False})
            continue
        for pattern in BUILD_TIMEOUT_PATTERNS:
            timeout_match = pattern.search(line.message)
            if timeout_match:
                waits.append({"millis": int(timeout_match.group("millis")), "timed_out": True})
                break
    return waits


def quiz_critique_gate_status(exercise_lines: List[LogLine], exercise_id: int, verifying_step_outputs: List[Dict[str, str]]) -> Dict[str, object]:
    """Whether the quiz half's semantic gate (the LLM self-critique) actually ran.

    Reported separately from the programming consistency check because they are different gates; using the
    consistency-check detector on a quiz run reports ``ran: false`` for every quiz, which is simply wrong.

    The critique has no positive log line, so "ran" means "invoked and not skipped": the gate runs on every
    verify attempt unless it logs one of its two skip paths (chat client unconfigured, or the call failed).
    A critique that passes cleanly reports no findings, so findings are corroborating evidence when
    present, never a precondition.
    """
    skipped_unconfigured = sum(1 for line in exercise_lines if QUIZ_CRITIQUE_SKIPPED_UNCONFIGURED.format(exercise_id=exercise_id) in line.raw)
    failed = sum(1 for line in exercise_lines if QUIZ_CRITIQUE_FAILED.format(exercise_id=exercise_id) in line.raw)
    with_findings = sum(1 for output in verifying_step_outputs if QUIZ_CRITIQUE_FINDING_MARKER in (output.get("detail") or ""))
    return {
        "gate": "QUIZ_CRITIQUE",
        "skipped_unconfigured": skipped_unconfigured,
        "skipped_after_failure": failed,
        "attempts_with_findings": with_findings,
        "evidence": "findings observed" if with_findings else "no skip logged",
        "ran": skipped_unconfigured == 0 and failed == 0,
    }


def semantic_gate_status(exercise_lines: List[LogLine], exercise_id: int, windowed_lines: List[LogLine]) -> Dict[str, object]:
    """Whether the consistency-check gate actually ran for this variant.

    The gate is best-effort and no-ops silently when the provider refuses the call, so "it was invoked"
    and "it produced a verdict" are recorded separately: a gate that starts, degrades, and returns an
    empty list looks identical to a clean pass in the job record.

    ``exercise_lines`` are id-scoped and exact. ``windowed_lines`` are the run's whole time window, needed
    only for the degradation line, which carries a prompt resource path rather than an exercise id — under
    concurrency that count can pick up a neighbouring run's degradation, so treat it as a flag to
    investigate rather than as an exact attribution.
    """
    started = sum(1 for line in exercise_lines if GATE_STARTED.format(exercise_id=exercise_id) in line.raw)
    completed = sum(1 for line in exercise_lines if GATE_COMPLETED_PATTERN.format(exercise_id=exercise_id) in line.raw)
    skipped = sum(1 for line in exercise_lines if GATE_SKIPPED in line.raw)
    degraded = sum(1 for line in windowed_lines if GATE_DEGRADED in line.raw)
    return {
        "started": started,
        "completed": completed,
        "skipped": skipped,
        "degraded_empty_response": degraded,
        "degraded_attribution": "time-windowed, not exercise-scoped",
        # Only a gate that started, completed, and never degraded actually gated anything.
        "ran": started > 0 and completed > 0 and degraded == 0 and skipped == 0,
    }