"""Renders one stored variant for reading — the qualitative half of the evaluation.

A green build says nothing about whether the generation produced what was asked for, so every judgement
in this evaluation (the pilot's defect list and the rubric alike) is made by reading the artifacts:
the problem statement, the diff against the source in all three repositories, and the automated checks
side by side.

Reads only stored artifacts, never the live instance, so a variant can be reviewed long after its run.

    python review.py results-pilot programming-C3-r0
"""

import difflib
import json
import os
import sys
from typing import Any, Dict, List, Optional

import checks

HERE = os.path.dirname(os.path.abspath(__file__))
SOURCES = os.path.join(HERE, "corpus", "sources")


def _read_json(path: str) -> Optional[Any]:
    if not os.path.exists(path):
        return None
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def _diff(label: str, source: Dict[str, Optional[str]], variant: Dict[str, Optional[str]], context: int = 3) -> None:
    added = sorted(set(variant) - set(source))
    removed = sorted(set(source) - set(variant))
    common = sorted(set(source) & set(variant))
    changed = [path for path in common if source[path] != variant[path]]
    identical = len(common) - len(changed)

    print(f"\n=== {label}: {identical}/{len(source)} source files byte-identical, {len(changed)} changed, {len(added)} added, {len(removed)} removed ===")
    for path in added:
        print(f"  + {path}")
    for path in removed:
        print(f"  - {path}   <-- removed from the source")
    for path in changed:
        print(f"\n--- {path} ---")
        lines = difflib.unified_diff(
            (source[path] or "").splitlines(),
            (variant[path] or "").splitlines(),
            fromfile=f"source/{path}",
            tofile=f"variant/{path}",
            lineterm="",
            n=context,
        )
        for line in lines:
            print("  " + line)


def review_programming(artifact_dir: str) -> None:
    exercise = _read_json(os.path.join(artifact_dir, "exercise.json")) or {}
    print(f"TITLE:      {exercise.get('title')}")
    print(f"SHORT NAME: {exercise.get('shortName')}")
    print(f"DIFFICULTY: {exercise.get('difficulty')}  (source: MEDIUM)")

    test_cases = _read_json(os.path.join(artifact_dir, "test-cases.json")) or []
    source_cases = _read_json(os.path.join(SOURCES, "programming-test-cases.json")) or []
    variant_names = {case.get("testName") for case in test_cases}
    source_names = {case.get("testName") for case in source_cases}
    print(f"\nTEST CASES: {len(variant_names)} (source {len(source_names)})")
    if variant_names - source_names:
        print(f"  added:   {sorted(variant_names - source_names)}")
    if source_names - variant_names:
        print(f"  removed: {sorted(source_names - variant_names)}")

    with open(os.path.join(artifact_dir, "problem-statement.md"), encoding="utf-8") as handle:
        statement = handle.read()
    result = checks.check_statement_integrity(statement, variant_names)
    print("\nAUTOMATED CHECKS:")
    for key, value in result.items():
        flag = "  " if not (value is True and key.startswith("has_") and key != "has_plantuml") else "!!"
        print(f"{flag} {key}: {value}")

    print("\n" + "=" * 100)
    print("PROBLEM STATEMENT")
    print("=" * 100)
    print(statement)

    for repository in ("template", "solution", "tests"):
        source_files = _read_json(os.path.join(SOURCES, f"programming-{repository}-files.json")) or {}
        variant_files = _read_json(os.path.join(artifact_dir, f"{repository}-files.json")) or {}
        _diff(repository.upper(), source_files, variant_files)


def review_quiz(artifact_dir: str) -> None:
    quiz = _read_json(os.path.join(artifact_dir, "quiz.json")) or {}
    source = _read_json(os.path.join(SOURCES, "quiz.json")) or {}
    print(f"TITLE:      {quiz.get('title')}")
    print(f"DIFFICULTY: {quiz.get('difficulty')}  (source: {source.get('difficulty')})")

    validity = checks.check_quiz_validity(quiz, len(source.get("quizQuestions") or []))
    print("\nAUTOMATED CHECKS:")
    for key, value in validity.items():
        print(f"  {key}: {value}")

    source_questions = source.get("quizQuestions") or []
    for index, question in enumerate(quiz.get("quizQuestions") or []):
        original = source_questions[index] if index < len(source_questions) else {}
        print("\n" + "=" * 100)
        print(f"Q{index + 1} [{question.get('type')}] points={question.get('points')} scoring={question.get('scoringType')}")
        print(f"  SOURCE TITLE:  {original.get('title')}")
        print(f"  VARIANT TITLE: {question.get('title')}")
        print(f"\n  SOURCE TEXT:\n{_indent(original.get('text') or '')}")
        print(f"\n  VARIANT TEXT:\n{_indent(question.get('text') or '')}")
        if question.get("type") == "multiple-choice":
            print("\n  OPTIONS (variant):")
            for option in question.get("answerOptions") or []:
                print(f"    [{'x' if option.get('isCorrect') else ' '}] {option.get('text')}")
        else:
            print(f"\n  SPOTS:     {[spot.get('spotNr') for spot in question.get('spots') or []]}")
            print(f"  SOLUTIONS: {[solution.get('text') for solution in question.get('solutions') or []]}")


def _indent(text: str, prefix: str = "    ") -> str:
    return "\n".join(prefix + line for line in text.splitlines())


def main() -> None:
    if len(sys.argv) != 3:
        print(__doc__)
        raise SystemExit(2)
    results_dir, run_id = sys.argv[1], sys.argv[2]
    artifact_dir = os.path.join(HERE, results_dir, "artifacts", run_id)
    if not os.path.isdir(artifact_dir):
        raise SystemExit(f"No artifacts for {run_id} — the run left no variant behind (FAILED, CANCELLED, or TIMEOUT).")
    if run_id.startswith("programming"):
        review_programming(artifact_dir)
    else:
        review_quiz(artifact_dir)


if __name__ == "__main__":
    main()
