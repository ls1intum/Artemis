from concurrent.futures import ThreadPoolExecutor, as_completed
import json
import os
import subprocess
import sys
import requests
from logging_config import logging
from typing import Dict, Any
from requests import Session

from utils import SERVER_URL, MAX_THREADS, MODEL_NAME, MODEL_EFFORT, CONSISTENCY_CHECK_EXERCISES, login_as_admin
from course import get_course_id_request, get_exercise_ids_request
from exercises import get_pecv_bench_dir

def consistency_check(session: requests.Session, exercise_ids: Dict[str, int]) -> str:
    """
    Run consistency checks for all programming exercises defined in consistency_check_exercises/config.ini

    ENSURE to run get_exercise_ids_request() BEFORE to retrieve the mapping of exercise names to their server IDs, which is required for the consistency check.

    :param requests.Session session: The authenticated session to use for API requests
    :param Dict[str, int] exercise_ids: Available programming exercises on COURSE_NAME with their server IDs
    :return: The approach ID used for the consistency check run
    :rtype: str
    """

    logging.info("Starting consistency checks for programming exercises...")

    pecv_bench_dir = get_pecv_bench_dir()
    if not os.listdir(pecv_bench_dir):
        logging.error(f"Step 12 failed: PECV-bench directory at {pecv_bench_dir} is empty. Execute Step 1 and Step 2 in exercises.py first")
        sys.exit(1)
    approach_id = ""
    try:
        branch_name = subprocess.check_output(
            ["git", "rev-parse", "--abbrev-ref", "HEAD"],
            text=True
        ).strip()
        short_commit = subprocess.check_output(
            ["git", "rev-parse", "--short", "HEAD"],
            text=True
        ).strip()
        # feature/hyperion/run-pecv-bench-in-artemis -> artemis-feature-hyperion-run_pecv_bench_in_artemis-abc1234
        approach_id = "artemis-" + branch_name.replace("-", "_").replace("/", "-") + "-" + short_commit
    except subprocess.CalledProcessError:
        logging.warning("Failed to determine git branch or commit. Using default approach ID.")
        approach_id = "artemis-default"

    model_name = MODEL_NAME
    run_id = f"{model_name}-{MODEL_EFFORT}"

    # Map exercise name -> (course, version) so the correct version is used per exercise
    consistency_check_exercises_dict: Dict[str, tuple[str, str]] = {}
    for version, courses in CONSISTENCY_CHECK_EXERCISES.items():
        for course, exercises in courses.items():
            for exercise in exercises:
                consistency_check_exercises_dict[exercise] = (course, version)
                os.makedirs(os.path.join(pecv_bench_dir, "results", approach_id, version, run_id, "cases", course, exercise), exist_ok=True)

    exercise_ids_filtered = {
        k: v for k, v in exercise_ids.items()
        if k.split(':', 1)[0] in consistency_check_exercises_dict
    }

    succeeded_count = 0
    failed_count = 0
    failed_checks: list[str] = []

    with ThreadPoolExecutor(max_workers=MAX_THREADS) as executor:
        future_to_local_id = {}

        for local_id, server_id in exercise_ids_filtered.items():
            exercise_name = local_id.split(':')[0]
            lookup = consistency_check_exercises_dict.get(exercise_name)

            if lookup is None:
                logging.warning(f"Could not find course for exercise {exercise_name}, skipping")
                continue

            course_name, dataset_version = lookup
            exercise_results_dir = os.path.join(pecv_bench_dir, "results", approach_id, dataset_version, run_id, "cases", course_name, exercise_name)

            future = executor.submit(
                consistency_check_io,
                session,
                SERVER_URL,
                local_id,
                server_id,
                exercise_results_dir,
                dataset_version,
                course_name,
                run_id
            )
            future_to_local_id[future] = local_id

        logging.info(f"Total variants to check: {len(future_to_local_id)}")

        for future in as_completed(future_to_local_id):
            local_id = future_to_local_id[future]
            try:
                result = future.result()
                if "success" in result:
                    succeeded_count += 1
                    logging.info(f"[OK]   {result}")
                else:
                    failed_count += 1
                    failed_checks.append(local_id)
                    logging.error(f"[FAIL] {result}")
            except Exception as e:
                failed_count += 1
                failed_checks.append(local_id)
                logging.exception(f"[FAIL] {local_id} — thread error: {e}")

    logging.info(f"Consistency checks completed: {succeeded_count}/{len(future_to_local_id)} succeeded.")
    if failed_checks:
        logging.error(f"Step 12 failed: {failed_count} consistency check(s) failed:\n" + "\n".join(f"  - {v}" for v in sorted(failed_checks)))
        logging.error("Execute Step 12 in consistency_check.py to retry")
    return approach_id

def consistency_check_io(session: Session, server_url: str, exercise_local_id: str, exercise_server_id: int, exercise_results_dir: str, dataset_version: str, course_name: str, run_id: str) -> str:
    """
    Worker function that triggers ``consistency_check_request`` and saves the result for a SINGLE variant.

    :param requests.Session session: The active requests Session object.
    :param str server_url: The base URL of the Artemis server.
    :param str exercise_local_id: The local variant identifier (e.g., 'H01E01-Lectures:001').
    :param int exercise_server_id: The ID of the programming exercise on the server.
    :param str exercise_results_dir: The directory where the result JSON file will be saved.
    :param str dataset_version: The dataset version identifier.
    :param str course_name: The course identifier.
    :param str run_id: The identifier for the current run.
    :return: A status string indicating success ('success') or failure ('error', 'Skipped ...').
    :rtype: str
    """
    if exercise_server_id is None:
        logging.error(f"Step 12 failed: Skipping {exercise_local_id} — missing exercise server ID. Execute Step 11 in course.py to re-fetch IDs, then execute Step 12")
        return f"Skipped {exercise_local_id} due to missing exercise ID"

    consistency_issue = consistency_check_request(session=session, exercise_server_id=exercise_server_id, server_url=server_url, exercise_local_id=exercise_local_id)
    exercise = exercise_local_id.split(":")[0]
    variant = exercise_local_id.split(":")[1]
    # Data Enrichment
    if consistency_issue is not None:
        consistency_issue["case_id"] = f"{dataset_version}/{course_name}/{exercise}/{variant}"
        consistency_issue["run_id"] = run_id

    # File Write (I/O)
    variant_json_file = os.path.join(exercise_results_dir, f"{variant}.json")
    try:
        with open(variant_json_file, "w", encoding="utf-8") as file:
            json.dump(consistency_issue, file, indent=4)
        return f"[{exercise_local_id}] success"
    except Exception as e:
        logging.exception(f"Step 12 failed: Failed to write result file for {exercise_local_id}: {e}. Execute Step 12 in consistency_check.py")
        return f"[{exercise_local_id}] error"

def consistency_check_request(session: requests.Session, server_url: str, exercise_server_id: int, exercise_local_id: str | None = None) -> Dict[str, Any] | None:
    """
    Server request, to check the consistency of the programming exercise with Hyperion System.

    POST /api/hyperion/programming-exercises/{exercise_server_id}/consistency-check

    :param requests.Session session: The active requests Session object.
    :param str server_url: The base URL of the Artemis server.
    :param int exercise_server_id: The ID of the programming exercise on the server.
    :param str exercise_local_id: (OPTIONAL) The local variant identifier (e.g., 'H01E01-Lectures:001').
    :return: A dictionary containing consistency issues, or ``None`` if the request failed.
    :rtype: Dict[str, Any] | None
    """
    debug_id = exercise_local_id if exercise_local_id is not None else exercise_server_id
    logging.info(f"[{debug_id}] 		Starting consistency check for programming exercise ID: {debug_id}")

    url: str = f"{server_url}/hyperion/programming-exercises/{exercise_server_id}/consistency-check"
    params = {"skipThreadContext": "true"}
    response: requests.Response = session.post(url, params=params)
    if response.status_code == 200:
        logging.info(f"[{debug_id}] Finished consistency check for programming exercise ID: {debug_id}")
        return response.json()
    else:
        logging.error(f"Step 12 failed: Consistency check request failed for {debug_id}; Status code: {response.status_code}\nResponse content: {response.text}")
        logging.error("Execute Step 12 in consistency_check.py")
        return None

if __name__ == "__main__":
    # Steps 5–6 (session/login), 8 (course ID), 11 (exercise IDs), and 12 (consistency check) are active.
    # Run: python consistency_check.py

    logging.info("Step 5: Creating session")
    session = requests.Session()

    logging.info("Step 6: Logging in as admin")
    login_as_admin(session=session)

    logging.info("Step 8: Retrieving Hyperion Benchmark Course ID")
    course_id = get_course_id_request(session=session)

    logging.info("Step 11: Retrieving programming exercise IDs for the course")
    exercise_ids = get_exercise_ids_request(session=session, course_id=course_id)

    logging.info("Step 12: Running consistency checks for all programming exercises")
    consistency_check(session=session, exercise_ids=exercise_ids)