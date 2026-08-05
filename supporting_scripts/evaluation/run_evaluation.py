"""CLI driver for the harness — the same entry points the notebook calls.

Runs are long (a programming run is minutes, and a quiz run is not much cheaper here), so every
invocation is meant to be started detached and left alone; the ledger is append-only and resumable, so
an interrupted client never loses a finished run's data.

    python run_evaluation.py one --type quiz --config C3 --round 0 --out results-pilot
    python run_evaluation.py round --round 1 --out results --concurrency 3
    python run_evaluation.py attach --job-id <uuid> --type quiz --config C3 --round 0 --out results-pilot

The unattended Stage 2 driver runs every round back to back and is the intended way to run the matrix:

    python run_evaluation.py matrix --rounds 1-6 --out results --concurrency 3

It is resumable: re-running the identical command after any interruption continues where it stopped,
because completed run ids are skipped and runs lost to a server restart are re-queued.
"""

import argparse
import json
import os
import signal
import subprocess
import sys
from datetime import datetime, timezone
from typing import List

from logging_config import logging
from matrix import CONFIGURATIONS, CONFIGURATIONS_BY_ID, EXERCISE_TYPES
from runner import Harness
from utils import authenticated_session


def prompt_commit_sha() -> str:
    """The commit the variant prompts are frozen at — recorded on every run line."""
    repo_root = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
    return subprocess.check_output(
        ["git", "log", "-1", "--format=%H", "--", "src/main/resources/prompts/hyperion/variants/"],
        cwd=repo_root,
        text=True,
    ).strip()


def parse_rounds(spec: str) -> List[int]:
    """Accepts "1-6" or "1,2,5" or "3"."""
    if "-" in spec:
        first, last = spec.split("-", 1)
        return list(range(int(first), int(last) + 1))
    return [int(part) for part in spec.split(",") if part.strip()]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", choices=["one", "round", "attach", "matrix"])
    parser.add_argument("--type", dest="exercise_type", choices=EXERCISE_TYPES)
    parser.add_argument("--config", dest="config_id")
    parser.add_argument("--round", dest="round_number", type=int, default=1)
    parser.add_argument("--out", dest="output_dir", default="results")
    parser.add_argument("--concurrency", type=int, default=None)
    parser.add_argument("--serial", action="store_true", help="mark the run as part of the serial timing subset")
    parser.add_argument("--job-id", dest="job_id", help="attach to an already-running job instead of starting one")
    parser.add_argument("--rounds", default="1-6", help="matrix only: rounds to run, e.g. 1-6 or 1,2,5")
    parser.add_argument("--configs", nargs="*", help="restrict a round to these configuration ids")
    parser.add_argument("--types", nargs="*", choices=EXERCISE_TYPES, help="restrict a round to these exercise types")
    args = parser.parse_args()

    session = authenticated_session()
    concurrency = args.concurrency if args.concurrency is not None else (3 if args.command in ("round", "matrix") else 1)
    harness = Harness(session, args.output_dir, prompt_commit_sha(), concurrency=concurrency)

    # These runs are long, so they get killed — with Ctrl-C interactively and with `pkill` when detached.
    # A killed client does not stop the server-side jobs, which then keep generating in the background and
    # overlap with whatever is started next. Turning SIGTERM into the same exception Ctrl-C raises lets the
    # round's handler cancel them on the way out, for both ways of stopping it.
    signal.signal(signal.SIGTERM, lambda _signum, _frame: (_ for _ in ()).throw(KeyboardInterrupt()))

    if args.command == "one":
        record = harness.run_one(args.exercise_type, args.config_id, args.round_number, serial=args.serial)
    elif args.command == "attach":
        # Recovers a run whose client died while the server-side job kept going. The job record's own
        # startedAt is authoritative, so nothing is lost by having missed the start.
        configuration = CONFIGURATIONS_BY_ID[args.config_id]
        detail, terminal = harness._poll_to_terminal(args.job_id, args.job_id)  # noqa: SLF001 — same module's internals
        now = datetime.now(timezone.utc)
        record = harness._collect(  # noqa: SLF001
            run_id=f"{args.exercise_type}-{args.config_id}-r{args.round_number}",
            job_id=args.job_id,
            exercise_type=args.exercise_type,
            configuration=configuration,
            round_number=args.round_number,
            serial=args.serial,
            detail=detail,
            terminal_phase=terminal,
            started_wall=now,
            finished_wall=now,
        )
    elif args.command == "matrix":
        config_ids = args.configs or [configuration.config_id for configuration in CONFIGURATIONS]
        exercise_types = args.types or list(EXERCISE_TYPES)
        harness.run_matrix(parse_rounds(args.rounds), config_ids, exercise_types)
        return
    else:
        config_ids = args.configs or [configuration.config_id for configuration in CONFIGURATIONS]
        exercise_types = args.types or list(EXERCISE_TYPES)
        records = harness.run_round(args.round_number, config_ids, exercise_types)
        logging.info("Round %s finished: %s runs", args.round_number, len(records))
        return

    print(json.dumps(record, indent=2, sort_keys=True, default=str))


if __name__ == "__main__":
    sys.exit(main())
