"""How far the matrix has got — safe to run at any time, from any terminal, while it is running.

Reads only the append-only ledger, so it never touches the run in progress.

    python status.py --out results
"""

import argparse
import collections
import json
import os
from datetime import datetime, timezone

from matrix import CONFIGURATIONS, EXERCISE_TYPES

# Outcomes that are re-queued rather than reported, so they must not count as progress.
RETRYABLE = {"LOST", "INVALID_ENVIRONMENT"}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", dest="output_dir", default="results")
    parser.add_argument("--rounds", type=int, default=6, help="rounds the matrix was launched with")
    args = parser.parse_args()

    path = os.path.join(args.output_dir, "runs.jsonl")
    if not os.path.exists(path):
        raise SystemExit(f"no ledger at {path} — nothing has been recorded yet")

    last = {}
    with open(path, encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if line:
                record = json.loads(line)
                last[record["run_id"]] = record

    done = [record for record in last.values() if record.get("terminal_phase") not in RETRYABLE]
    expected = len(CONFIGURATIONS) * len(EXERCISE_TYPES) * args.rounds
    phases = collections.Counter(record["terminal_phase"] for record in done)
    quarantined = sum(1 for record in last.values() if record.get("terminal_phase") in RETRYABLE)

    print(f"{len(done)}/{expected} runs recorded ({100 * len(done) / expected:.0f}%)")
    print(f"  outcomes: {dict(phases)}")
    if quarantined:
        print(f"  quarantined and re-queued: {quarantined}")

    by_round = collections.Counter(record["round"] for record in done)
    per_round = len(CONFIGURATIONS) * len(EXERCISE_TYPES)
    for round_number in sorted(by_round):
        count = by_round[round_number]
        print(f"  round {round_number}: {count}/{per_round}{'' if count < per_round else '  complete'}")

    timestamps = [record["finished_at"] for record in done if record.get("finished_at")]
    if len(timestamps) >= 2:
        first = min(datetime.fromisoformat(value) for value in timestamps)
        latest = max(datetime.fromisoformat(value) for value in timestamps)
        elapsed = (latest - first).total_seconds()
        rate = elapsed / max(len(done) - 1, 1)
        remaining = rate * (expected - len(done))
        stale = (datetime.now(timezone.utc) - latest).total_seconds()
        print(f"\n  {elapsed / 3600:.1f} h elapsed, ~{remaining / 3600:.1f} h left at the observed rate")
        print(f"  last run finished {stale / 60:.0f} min ago" + ("  <-- nothing for a while, check the runner is alive" if stale > 3600 else ""))


if __name__ == "__main__":
    main()
