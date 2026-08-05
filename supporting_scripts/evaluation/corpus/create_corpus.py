"""Builds the evaluation corpus: a dedicated course, the two source exercises, and the two variant groups.

Run from ``supporting_scripts/evaluation`` with the virtualenv active:

    python corpus/create_corpus.py

Writes ``corpus/corpus.json`` (the ids every later stage reads) and snapshots both sources under
``corpus/sources/`` so the report can describe them without a live instance.

The programming source is Artemis's canonical Java strategy-pattern sorting exercise, created fresh for
this evaluation rather than taken from a course: ``SortStrategy`` with ``BubbleSort`` and ``MergeSort``,
a ``Context`` holding the dates, and a ``Policy`` choosing the strategy, with structural tests driven by
a ``test.json`` structure oracle plus behaviour tests, task markers referencing the real test names, and
a PlantUML diagram with ``testsColor(...)`` annotations. ``Context``, ``Policy`` and ``SortStrategy``
have no file in the template repository at all; ``BubbleSort`` and ``MergeSort`` ship as stubs, because
the behaviour test references those two types at compile time and the template must still compile.

Starting invariants are verified before the corpus is usable: the solution scores 100 % and the
template compiles and scores 0 %.
"""

import json
import os
import sys
import time
from typing import Any, Dict, List, Optional

import requests
import urllib3

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import artifacts  # noqa: E402
from logging_config import logging  # noqa: E402
from quiz_questions import QUIZ_QUESTIONS  # noqa: E402
from utils import (  # noqa: E402
    COURSE_NAME,
    COURSE_SHORT_NAME,
    CORPUS_PATH,
    PACKAGE_NAME,
    PROGRAMMING_SHORT_NAME,
    PROGRAMMING_TITLE,
    QUIZ_TITLE,
    SERVER_URL,
    authenticated_session,
    expect,
)

HERE = os.path.dirname(os.path.abspath(__file__))
SOURCES_DIR = os.path.join(HERE, "sources")
JAVA_TEMPLATE_PATH = os.path.join(HERE, "..", "..", "..", "src", "test", "playwright", "fixtures", "exercise", "programming", "java", "template.json")

BUILD_POLL_SECONDS = 5
BUILD_TIMEOUT_SECONDS = 900


def get_or_create_course(session: requests.Session) -> Dict[str, Any]:
    existing = expect(session.get(f"{SERVER_URL}/core/courses/for-notifications"), 200).json()
    for course in existing:
        if course.get("shortName") == COURSE_SHORT_NAME:
            logging.info("Reusing existing course %s (id %s)", COURSE_SHORT_NAME, course["id"])
            return course

    course_payload = {
        "title": COURSE_NAME,
        "shortName": COURSE_SHORT_NAME,
        "customizeGroupNames": True,
        "studentGroupName": "students",
        "teachingAssistantGroupName": "tutors",
        "editorGroupName": "editors",
        "instructorGroupName": "instructors",
        "testCourse": True,
        "onlineCourse": False,
        "accuracyOfScores": 1,
        "maxComplaints": 3,
        "maxTeamComplaints": 3,
        "maxComplaintTimeDays": 7,
        "maxComplaintTextLimit": 2000,
        "maxComplaintResponseTextLimit": 2000,
        "maxRequestMoreFeedbackTimeDays": 7,
        "courseInformationSharingConfiguration": "COMMUNICATION_AND_MESSAGING",
        "enrollmentEnabled": False,
    }
    fields = {"course": ("blob.json", json.dumps(course_payload), "application/json")}
    body, content_type = urllib3.filepost.encode_multipart_formdata(fields)
    response = expect(session.post(f"{SERVER_URL}/core/admin/courses", data=body, headers={"Content-Type": content_type}), 201)
    course = response.json()
    logging.info("Created course %s (id %s)", COURSE_SHORT_NAME, course["id"])
    return course


def find_exercise(session: requests.Session, course_id: int, title: str) -> Optional[Dict[str, Any]]:
    course = expect(session.get(f"{SERVER_URL}/core/courses/{course_id}/for-registration" if False else f"{SERVER_URL}/core/courses/{course_id}/with-exercises"), 200).json()
    for exercise in course.get("exercises", []) or []:
        if exercise.get("title") == title:
            return exercise
    return None


def create_programming_exercise(session: requests.Session, course: Dict[str, Any]) -> Dict[str, Any]:
    existing = find_exercise(session, course["id"], PROGRAMMING_TITLE)
    if existing:
        logging.info("Reusing programming exercise %s (id %s)", PROGRAMMING_TITLE, existing["id"])
        return existing

    with open(os.path.normpath(JAVA_TEMPLATE_PATH), encoding="utf-8") as template_file:
        exercise = json.load(template_file)

    exercise.update(
        {
            "title": PROGRAMMING_TITLE,
            "shortName": PROGRAMMING_SHORT_NAME,
            "packageName": PACKAGE_NAME,
            "channelName": f"exercise-{PROGRAMMING_SHORT_NAME}",
            # MEDIUM so that both EASY (C1) and HARD (C2) are meaningful moves.
            "difficulty": "MEDIUM",
            "course": course,
        }
    )
    response = expect(session.post(f"{SERVER_URL}/programming/programming-exercises/setup", json=exercise), 201)
    created = response.json()
    logging.info("Created programming exercise %s (id %s)", PROGRAMMING_TITLE, created["id"])
    return created


def _latest_result(participation: Dict[str, Any]) -> Optional[Dict[str, Any]]:
    """Latest result of a base participation.

    Results hang off the participation's *submissions*, not off the participation itself — the
    participation's own ``results`` field is null even with ``withSubmissionResults=true``.
    """
    submissions = participation.get("submissions") or []
    results = [result for submission in submissions for result in (submission.get("results") or [])]
    if not results:
        return None
    return max(results, key=lambda result: result.get("completionDate") or "")


def wait_for_base_results(session: requests.Session, exercise_id: int) -> Dict[str, Dict[str, Any]]:
    """Polls until both base participations carry a result; returns the two results."""
    deadline = time.time() + BUILD_TIMEOUT_SECONDS
    while time.time() < deadline:
        exercise = expect(
            session.get(f"{SERVER_URL}/programming/programming-exercises/{exercise_id}/with-template-and-solution-participation", params={"withSubmissionResults": "true"}),
            200,
        ).json()
        results = {key: _latest_result(exercise.get(participation_key) or {}) for key, participation_key in (("solution", "solutionParticipation"), ("template", "templateParticipation"))}
        if results["solution"] and results["template"]:
            return results
        logging.info("Waiting for base builds (solution=%s, template=%s)...", bool(results["solution"]), bool(results["template"]))
        time.sleep(BUILD_POLL_SECONDS)
    raise RuntimeError(f"Base builds for exercise {exercise_id} did not finish within {BUILD_TIMEOUT_SECONDS}s")


def verify_programming_invariants(session: requests.Session, exercise_id: int) -> Dict[str, Any]:
    results = wait_for_base_results(session, exercise_id)
    solution, template = results["solution"], results["template"]
    summary = {
        "solution_score": solution.get("score"),
        "template_score": template.get("score"),
        "test_case_count": solution.get("testCaseCount"),
        "solution_passed_test_cases": solution.get("passedTestCaseCount"),
        "template_passed_test_cases": template.get("passedTestCaseCount"),
    }
    logging.info("Base build results: %s", summary)
    if summary["solution_score"] != 100:
        raise RuntimeError(f"Source exercise invariant violated: solution scored {summary['solution_score']}%, expected 100%")
    if summary["template_score"] != 0:
        raise RuntimeError(f"Source exercise invariant violated: template scored {summary['template_score']}%, expected 0%")
    # A template that fails to compile also scores 0 %; the exercise is only a usable baseline if it compiles.
    template_submission_failed = any(
        submission.get("buildFailed")
        for submission in (expect(session.get(f"{SERVER_URL}/programming/programming-exercises/{exercise_id}/with-template-and-solution-participation", params={"withSubmissionResults": "true"}), 200).json().get("templateParticipation") or {}).get("submissions")
        or []
    )
    if template_submission_failed:
        raise RuntimeError("Source exercise invariant violated: template build failed to compile rather than scoring 0 %")
    return summary


def create_quiz_exercise(session: requests.Session, course: Dict[str, Any]) -> Dict[str, Any]:
    existing = find_exercise(session, course["id"], QUIZ_TITLE)
    if existing:
        logging.info("Reusing quiz exercise %s (id %s)", QUIZ_TITLE, existing["id"])
        return existing

    quiz = {
        "title": QUIZ_TITLE,
        "difficulty": "MEDIUM",
        "mode": "INDIVIDUAL",
        "includedInOverallScore": "INCLUDED_COMPLETELY",
        "channelName": "exercise-designpatternsquiz",
        "randomizeQuestionOrder": False,
        "quizMode": "SYNCHRONIZED",
        "duration": 900,
        "quizQuestions": QUIZ_QUESTIONS,
    }
    fields = {"exercise": ("blob.json", json.dumps(quiz), "application/json")}
    body, content_type = urllib3.filepost.encode_multipart_formdata(fields)
    response = expect(session.post(f"{SERVER_URL}/quiz/courses/{course['id']}/quiz-exercises", data=body, headers={"Content-Type": content_type}), 201)
    created = response.json()
    logging.info("Created quiz exercise %s (id %s) with %s questions", QUIZ_TITLE, created["id"], len(QUIZ_QUESTIONS))
    return created


def get_or_create_variant_group(session: requests.Session, course_id: int, title: str) -> Dict[str, Any]:
    groups = expect(session.get(f"{SERVER_URL}/exercise/courses/{course_id}/exercise-variant-groups"), 200).json()
    for group in groups:
        if group.get("title") == title:
            logging.info("Reusing variant group '%s' (id %s)", title, group["id"])
            return group
    response = expect(session.post(f"{SERVER_URL}/exercise/courses/{course_id}/exercise-variant-groups", json={"title": title}), 201)
    group = response.json()
    logging.info("Created variant group '%s' (id %s)", title, group["id"])
    return group


def snapshot_sources(session: requests.Session, programming_id: int, quiz_id: int) -> None:
    os.makedirs(SOURCES_DIR, exist_ok=True)

    exercise = expect(session.get(f"{SERVER_URL}/programming/programming-exercises/{programming_id}"), 200).json()
    with open(os.path.join(SOURCES_DIR, "programming-problem-statement.md"), "w", encoding="utf-8") as handle:
        handle.write(exercise.get("problemStatement") or "")

    for repo in ("template", "solution"):
        files = expect(session.get(f"{SERVER_URL}/programming/programming-exercises/{programming_id}/{repo}-files-content"), 200).json()
        with open(os.path.join(SOURCES_DIR, f"programming-{repo}-files.json"), "w", encoding="utf-8") as handle:
            json.dump(files, handle, indent=2, sort_keys=True)

    # Deliberately the same function the variant capture uses: the test repository has no bulk endpoint,
    # and fetching the source any other way would make the byte-identical comparison a shape difference
    # rather than a content difference.
    test_files = artifacts.fetch_test_repository_files(session, programming_id)
    with open(os.path.join(SOURCES_DIR, "programming-tests-files.json"), "w", encoding="utf-8") as handle:
        json.dump(test_files, handle, indent=2, sort_keys=True)

    test_cases = expect(session.get(f"{SERVER_URL}/programming/programming-exercises/{programming_id}/test-cases"), 200).json()
    with open(os.path.join(SOURCES_DIR, "programming-test-cases.json"), "w", encoding="utf-8") as handle:
        json.dump(test_cases, handle, indent=2, sort_keys=True)

    quiz = expect(session.get(f"{SERVER_URL}/quiz/quiz-exercises/{quiz_id}"), 200).json()
    with open(os.path.join(SOURCES_DIR, "quiz.json"), "w", encoding="utf-8") as handle:
        json.dump(quiz, handle, indent=2, sort_keys=True)
    logging.info("Snapshotted both sources under %s", SOURCES_DIR)


def main() -> None:
    session = authenticated_session()
    course = get_or_create_course(session)

    programming = create_programming_exercise(session, course)
    verification = verify_programming_invariants(session, programming["id"])
    quiz = create_quiz_exercise(session, course)

    programming_group = get_or_create_variant_group(session, course["id"], "Sorting Strategy Variants")
    quiz_group = get_or_create_variant_group(session, course["id"], "Design Patterns Quiz Variants")

    snapshot_sources(session, programming["id"], quiz["id"])

    corpus = {
        "course_id": course["id"],
        "course_short_name": COURSE_SHORT_NAME,
        "programming_exercise_id": programming["id"],
        "programming_title": PROGRAMMING_TITLE,
        # C1 (EASY) and C2 (HARD) are only meaningful relative to the source's own difficulty.
        "programming_difficulty": programming.get("difficulty"),
        "quiz_difficulty": quiz.get("difficulty"),
        "programming_base_verification": verification,
        "quiz_exercise_id": quiz["id"],
        "quiz_title": QUIZ_TITLE,
        "quiz_question_count": len(QUIZ_QUESTIONS),
        "programming_group_id": programming_group["id"],
        "quiz_group_id": quiz_group["id"],
    }
    with open(CORPUS_PATH, "w", encoding="utf-8") as handle:
        json.dump(corpus, handle, indent=2, sort_keys=True)
    logging.info("Wrote %s", CORPUS_PATH)
    print(json.dumps(corpus, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
