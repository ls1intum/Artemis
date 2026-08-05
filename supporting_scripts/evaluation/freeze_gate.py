"""Evaluates the Stage 1 freeze gate for one pilot round.

The gate is the work order's precondition for freezing the prompts and starting the measured matrix:

    - at least 4 of 5 programming runs reach COMPLETED
    - no run ships a stray <testid> in its problem statement
    - no run ships a dangling task reference (a name matching no test case)
    - no run ships fenced PlantUML

Reported per criterion rather than as a single boolean, because a near miss and a broad failure call for
different next steps and the round costs ~30 minutes to repeat.

    python freeze_gate.py --round 8 --out results-pilot
"""

import argparse
import collections
import json
import os
from typing import Any, Dict, List

from checks import run_checks_for_run

# Runs quarantined as environment failures are not evidence about the prompts either way; the gate must
# neither credit nor blame them. A round with any is not a valid gate round at all.
QUARANTINED_PHASES = ("INVALID_ENVIRONMENT",)


def load_round(output_dir: str, round_number: int, exercise_type: str) -> List[Dict[str, Any]]:
    """Last-wins over the append-only ledger, restricted to one round and type."""
    path = os.path.join(output_dir, "runs.jsonl")
    last: Dict[str, Dict[str, Any]] = {}
    with open(path, encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            record = json.loads(line)
            if record.get("round") == round_number and record.get("exercise_type") == exercise_type:
                last[record["run_id"]] = record
    return sorted(last.values(), key=lambda record: record["run_id"])


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--round", dest="round_number", type=int, required=True)
    parser.add_argument("--out", dest="output_dir", default="results-pilot")
    parser.add_argument("--type", dest="exercise_type", default="programming")
    parser.add_argument("--configs", nargs="*", help="restrict to these configuration ids")
    args = parser.parse_args()

    records = load_round(args.output_dir, args.round_number, args.exercise_type)
    if args.configs:
        records = [record for record in records if record["config_id"] in args.configs]
    if not records:
        raise SystemExit(f"no {args.exercise_type} runs recorded for round {args.round_number}")

    artifacts_root = os.path.join(args.output_dir, "artifacts")
    sources_dir = os.path.join("corpus", "sources")

    phases = collections.Counter(record["terminal_phase"] for record in records)
    quarantined = [record for record in records if record["terminal_phase"] in QUARANTINED_PHASES]
    completed = [record for record in records if record["terminal_phase"] == "COMPLETED"]

    stray_testid, dangling, fenced, gutters = [], [], [], []
    for record in records:
        if record["terminal_phase"] in QUARANTINED_PHASES:
            continue
        checks = run_checks_for_run(record, artifacts_root, sources_dir)
        statement = checks.get("statement")
        if not statement:
            continue
        run_id = record["run_id"]
        if statement["has_stray_testid"]:
            stray_testid.append((run_id, statement["stray_testid_count"]))
        if statement["has_dangling_task_reference"]:
            dangling.append((run_id, statement["dangling_task_references"]))
        if statement["has_fenced_plantuml"]:
            fenced.append(run_id)
        if statement["has_line_number_gutters"]:
            gutters.append((run_id, statement["gutter_line_count"]))

    total = len(records)
    print(f"Round {args.round_number} ({args.exercise_type}), {total} runs")
    print(f"  phases: {dict(phases)}")
    if quarantined:
        print(f"  !! {len(quarantined)} run(s) quarantined as environment failures — NOT a valid gate round")

    # The work order states the criterion as "at least 4 of 5", i.e. 80%. Expressed as a ratio so a round run on
    # a reduced configuration subset is judged on the same bar rather than on an absolute count it cannot reach.
    required = 0.8
    criteria = [
        (f"completed >= 80% ({len(completed)}/{total})", total > 0 and len(completed) / total >= required),
        (f"no stray <testid> ({len(stray_testid)} run(s))", not stray_testid),
        (f"no dangling task reference ({len(dangling)} run(s))", not dangling),
        (f"no fenced PlantUML ({len(fenced)} run(s))", not fenced),
    ]
    print()
    for label, passed in criteria:
        print(f"  [{'PASS' if passed else 'FAIL'}] {label}")
    # Not a gate criterion, but D6's regression signal: gutters should now be structurally impossible.
    print(f"  [ -- ] line-number gutters ({len(gutters)} run(s)) — regression watch, not a gate criterion")

    for run_id, names in dangling:
        print(f"\n  dangling in {run_id}: {names}")
    for run_id, count in stray_testid:
        print(f"\n  stray <testid> in {run_id}: {count}")
    for run_id, count in gutters:
        print(f"\n  gutters in {run_id}: {count} line(s)")

    print()
    gate_passed = all(passed for _, passed in criteria) and not quarantined
    print("GATE PASSED" if gate_passed else "GATE NOT PASSED")


if __name__ == "__main__":
    main()
