"""Artifact capture for a finished run.

Only runs that left a variant behind have artifacts; FAILED, CANCELLED, and TIMEOUT runs have none
(the hard-failure and cancel paths delete the provisioned clone and clear the exercise id).

Programming artifacts are captured through the REST API rather than by cloning: the template and
solution repositories come from the exercise-level ``*-files-content`` endpoints, the test repository is
enumerated and fetched file by file. That keeps ~240 git clones off the laptop while still storing the
full content the rubric and the automated checks need.
"""

import json
import os
from typing import Any, Dict, Optional

import requests

from logging_config import logging
from utils import SERVER_URL, expect


def _write_json(path: str, payload: Any) -> None:
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, indent=2, sort_keys=True)


def fetch_test_repository_files(session: requests.Session, exercise_id: int) -> Dict[str, Optional[str]]:
    """Test repository content, enumerated then fetched file by file.

    The test repository has no bulk ``*-files-content`` equivalent. Both the sources and the variants go
    through this one function so the two sides of the byte-identical comparison have the same shape — a
    source fetched any other way would make that diff noise rather than signal.

    A file that could not be read is stored as ``None`` rather than omitted, so an incomplete capture is
    visible in the artifact instead of looking like a deleted file.
    """
    listing = expect(session.get(f"{SERVER_URL}/programming/programming-exercises/{exercise_id}/test-repository/files"), 200).json()
    files: Dict[str, Optional[str]] = {}
    for path, entry_type in sorted(listing.items()):
        if entry_type != "FILE":
            continue
        response = session.get(f"{SERVER_URL}/programming/programming-exercises/{exercise_id}/test-repository/file", params={"file": path})
        files[path] = response.text if response.status_code == 200 else None
        if response.status_code != 200:
            logging.warning("Could not read test file %s of exercise %s: %s", path, exercise_id, response.status_code)
    return files


def capture_programming(session: requests.Session, exercise_id: int, target_dir: str) -> Dict[str, Any]:
    os.makedirs(target_dir, exist_ok=True)
    exercise = expect(session.get(f"{SERVER_URL}/programming/programming-exercises/{exercise_id}"), 200).json()

    with open(os.path.join(target_dir, "problem-statement.md"), "w", encoding="utf-8") as handle:
        handle.write(exercise.get("problemStatement") or "")
    _write_json(os.path.join(target_dir, "exercise.json"), exercise)

    for repository in ("template", "solution"):
        files = expect(session.get(f"{SERVER_URL}/programming/programming-exercises/{exercise_id}/{repository}-files-content"), 200).json()
        _write_json(os.path.join(target_dir, f"{repository}-files.json"), files)

    test_files = fetch_test_repository_files(session, exercise_id)
    _write_json(os.path.join(target_dir, "tests-files.json"), test_files)

    # The test cases Artemis itself knows about, which is what a task marker must resolve against. Stored
    # now rather than recovered later: recovering it would make the analysis depend on this instance's
    # database still being intact weeks from now, which is the opposite of why artifacts are stored.
    test_cases = expect(session.get(f"{SERVER_URL}/programming/programming-exercises/{exercise_id}/test-cases"), 200).json()
    _write_json(os.path.join(target_dir, "test-cases.json"), test_cases)

    unreadable = sorted(path for path, content in test_files.items() if content is None)
    return {
        "problem_statement_chars": len(exercise.get("problemStatement") or ""),
        "test_file_count": sum(1 for value in test_files.values() if value is not None),
        "unreadable_test_files": unreadable,
        "test_case_count": len(test_cases),
        "difficulty": exercise.get("difficulty"),
        "title": exercise.get("title"),
    }


def capture_quiz(session: requests.Session, exercise_id: int, target_dir: str) -> Dict[str, Any]:
    os.makedirs(target_dir, exist_ok=True)
    quiz = expect(session.get(f"{SERVER_URL}/quiz/quiz-exercises/{exercise_id}"), 200).json()
    _write_json(os.path.join(target_dir, "quiz.json"), quiz)
    return {
        "question_count": len(quiz.get("quizQuestions") or []),
        "difficulty": quiz.get("difficulty"),
        "title": quiz.get("title"),
    }


def capture(session: requests.Session, exercise_type: str, exercise_id: int, target_dir: str) -> Dict[str, Any]:
    if exercise_type == "programming":
        return capture_programming(session, exercise_id, target_dir)
    return capture_quiz(session, exercise_id, target_dir)