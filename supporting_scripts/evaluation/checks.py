"""The three automated checks over stored artifacts — the cheap half of quality measurement.

These run over every surviving variant at no marginal cost, which is what lets the rubric's scarce
reading attention go to the criteria no check can reach (intent fidelity, readiness for use).

1. Statement integrity — stray ``<testid>`` tags, task markers naming tests that do not exist in the
   variant, and PlantUML wrapped in a code fence.
2. Transform-not-regenerate — the fraction of source files left byte-identical in the variant.
3. Quiz validity — at least one correct option per multiple-choice question, spots with mapped
   solutions for short answer, and the question count against the source.

Everything is computed from the stored artifacts only, never from the live instance.
"""

import json
import os
import re
from typing import Any, Dict, List, Optional, Set

# Greedy to the last ")" on the line, not to the first: a reference list can itself contain parentheses.
# The defect D4 produces exactly that ("testBubbleSort()"), and a first-paren match silently truncates the
# name to "testBubbleSort(", which still reports as dangling but quotes corrupted evidence. Task markers
# occupy their own line, so anchoring to the line end is safe.
TASK_MARKER_PATTERN = re.compile(r"\[task\]\[[^\]]*\]\((.*)\)\s*$", re.MULTILINE)
TESTID_PATTERN = re.compile(r"<testid>")
# Line-number gutters echoed back from the rendered context (" 1 | text"). Markdown numbered lists use
# "1." and table rows start with "|", so a digit followed by " | " at line start is unambiguous.
GUTTER_PATTERN = re.compile(r"^[ \t]*\d+ \| ", re.MULTILINE)
# PlantUML must be rendered by Artemis, so it must not sit inside a fenced code block.
FENCED_PLANTUML_PATTERN = re.compile(r"```[^\n]*\n(?:(?!```)[\s\S])*?@startuml", re.MULTILINE)


def _read_json(path: str) -> Optional[Any]:
    if not os.path.exists(path):
        return None
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def _read_text(path: str) -> Optional[str]:
    if not os.path.exists(path):
        return None
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def parse_task_references(problem_statement: str) -> List[str]:
    """Every test name referenced by a ``[task][...](refs)`` marker, split on commas."""
    references: List[str] = []
    for group in TASK_MARKER_PATTERN.findall(problem_statement):
        references.extend(reference.strip() for reference in group.split(",") if reference.strip())
    return references


def check_statement_integrity(problem_statement: str, test_case_names: Set[str]) -> Dict[str, Any]:
    references = parse_task_references(problem_statement)
    dangling = sorted({reference for reference in references if reference not in test_case_names})
    testid_hits = TESTID_PATTERN.findall(problem_statement)
    fenced = FENCED_PLANTUML_PATTERN.findall(problem_statement)
    gutters = GUTTER_PATTERN.findall(problem_statement)
    return {
        "gutter_line_count": len(gutters),
        "has_line_number_gutters": bool(gutters),
        "task_reference_count": len(references),
        "dangling_task_references": dangling,
        "has_dangling_task_reference": bool(dangling),
        "stray_testid_count": len(testid_hits),
        "has_stray_testid": bool(testid_hits),
        "has_fenced_plantuml": bool(fenced),
        "has_plantuml": "@startuml" in problem_statement,
    }


def check_byte_identical_fraction(source_files: Dict[str, Optional[str]], variant_files: Dict[str, Optional[str]]) -> Dict[str, Any]:
    """How much of the source survived untouched — the transform-not-regenerate property.

    Measured over the source's files: a file the variant renamed or dropped counts as not identical,
    which is the intended reading (it was not left alone).
    """
    if not source_files:
        return {"source_file_count": 0, "identical_count": 0, "identical_fraction": None}
    identical = [path for path, content in source_files.items() if path in variant_files and variant_files[path] == content]
    return {
        "source_file_count": len(source_files),
        "identical_count": len(identical),
        "identical_fraction": len(identical) / len(source_files),
        "changed_or_missing": sorted(set(source_files) - set(identical)),
        "added_files": sorted(set(variant_files) - set(source_files)),
    }


def check_quiz_validity(quiz: Dict[str, Any], source_question_count: int) -> Dict[str, Any]:
    """Mirrors the server-side validity rules the pipeline's quiz gate enforces."""
    questions = quiz.get("quizQuestions") or []
    problems: List[str] = []
    for index, question in enumerate(questions, start=1):
        question_type = question.get("type")
        title = question.get("title") or f"question {index}"
        if question_type == "multiple-choice":
            options = question.get("answerOptions") or []
            correct = [option for option in options if option.get("isCorrect")]
            if not correct:
                problems.append(f"{title}: no correct option")
            # Single-choice questions must have exactly one correct option and ALL_OR_NOTHING scoring.
            if question.get("singleChoice"):
                if len(correct) != 1:
                    problems.append(f"{title}: singleChoice with {len(correct)} correct options")
                if question.get("scoringType") != "ALL_OR_NOTHING":
                    problems.append(f"{title}: singleChoice with scoringType {question.get('scoringType')}")
        elif question_type == "short-answer":
            spots = question.get("spots") or []
            mappings = question.get("correctMappings") or []
            if not spots:
                problems.append(f"{title}: no spots")
            if not mappings:
                problems.append(f"{title}: no correct mappings")
            mapped_spot_numbers = {
                (mapping.get("spot") or {}).get("spotNr")
                for mapping in mappings
            }
            for spot in spots:
                if spot.get("spotNr") not in mapped_spot_numbers:
                    problems.append(f"{title}: spot {spot.get('spotNr')} has no mapped solution")
        elif question_type == "drag-and-drop":
            problems.append(f"{title}: drag-and-drop is unsupported and should never be generated")
    return {
        "question_count": len(questions),
        "source_question_count": source_question_count,
        "question_count_matches_source": len(questions) == source_question_count,
        "validity_problems": problems,
        "is_valid": not problems,
    }


def run_checks_for_run(run_record: Dict[str, Any], artifacts_root: str, sources_dir: str) -> Dict[str, Any]:
    """All applicable checks for one run; returns ``None`` markers where a run left no variant."""
    run_id = run_record["run_id"]
    artifact_dir = os.path.join(artifacts_root, run_id)
    if not os.path.isdir(artifact_dir):
        return {"run_id": run_id, "has_artifacts": False}

    result: Dict[str, Any] = {"run_id": run_id, "has_artifacts": True, "exercise_type": run_record["exercise_type"], "config_id": run_record["config_id"]}

    if run_record["exercise_type"] == "programming":
        statement = _read_text(os.path.join(artifact_dir, "problem-statement.md")) or ""
        test_cases = _read_json(os.path.join(artifact_dir, "test-cases.json")) or []
        test_case_names = {case.get("testName") for case in test_cases}
        result["statement"] = check_statement_integrity(statement, test_case_names)
        for repository in ("template", "solution", "tests"):
            source = _read_json(os.path.join(sources_dir, f"programming-{repository}-files.json")) or {}
            variant = _read_json(os.path.join(artifact_dir, f"{repository}-files.json")) or {}
            result[f"{repository}_preservation"] = check_byte_identical_fraction(source, variant)
    else:
        quiz = _read_json(os.path.join(artifact_dir, "quiz.json")) or {}
        source_quiz = _read_json(os.path.join(sources_dir, "quiz.json")) or {}
        result["quiz"] = check_quiz_validity(quiz, len(source_quiz.get("quizQuestions") or []))

    return result
