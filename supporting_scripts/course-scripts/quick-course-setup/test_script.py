import json
from uuid import uuid4
from requests import Session
import time
import configparser
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import Dict, Callable, Any, List, Tuple
from logging_config import logging
from utils import add_user_to_course, login_as_admin, authenticate_user
from add_users_to_course import add_users_to_groups_of_course
from manage_programming_exercise import create_single_programming_exercise
from admin_operations import (
    get_build_job_statistics_for_course,
    get_queued_build_jobs_for_course,
    get_running_build_jobs_for_course,
    get_submissions_for_exercise,
    get_all_results,
    log_build_agent_summaries,
)
from experiment_config import JAVA_HAPPY_PATH, JAVA_TIMEOUT_BUILD, JAVA_FAILING_BUILD
from student_operations import participate_programming_exercise
import urllib3

config = configparser.ConfigParser()
config.read("config.ini")
secrets = configparser.ConfigParser()
secrets.read("secrets.ini")

SERVER_URL: str = config.get("Settings", "server_url")
CLIENT_URL: str = config.get("Settings", "client_url")

USER_NAME_PATTERN: str = secrets.get(
    "User", "artemis_test_user_pattern", fallback="artemis_test_user_{}"
)
PASSWORD_PATTERN: str = secrets.get(
    "User", "artemis_test_user_password_pattern", fallback="artemis_test_user_{}"
)


def authenticate_single_user(user_index: int) -> tuple[int, Session]:
    """Authenticate a single user and return the session."""
    session = Session()
    username = USER_NAME_PATTERN.format(user_index)
    password = PASSWORD_PATTERN.format(user_index)

    try:
        authenticate_user(username, password, session)
        return user_index, session
    except Exception as e:
        logging.error(f"Failed to authenticate user {username}: {e}")
        return user_index, None


def authenticate_users(user_count: int, max_workers=4) -> Dict[int, Session]:
    user_sessions = {}
    test_user_range = range(1, user_count + 1)
    with ThreadPoolExecutor(max_workers=max_workers) as executor:
        future_to_user = {
            executor.submit(authenticate_single_user, i): i for i in test_user_range
        }

        for future in as_completed(future_to_user):
            user_index, session = future.result()
            if session is not None:
                user_sessions[user_index] = session
            else:
                logging.error(f"Failed to store session for user {user_index}")

    logging.info(
        f"Parallel authentication completed. {len(user_sessions)} users authenticated successfully."
    )
    return user_sessions


def run_operation_for_student(
    user_index: int, session: Session, operation: Callable, *args, **kwargs
) -> Tuple[int, Any]:
    """Execute an operation for a single student and return the result."""
    try:
        result = operation(session, *args, **kwargs)
        logging.debug(f"Operation completed successfully for user {user_index}")
        return user_index, result
    except Exception as e:
        logging.error(f"Operation failed for user {user_index}: {e}")
        return user_index, None


def run_parallel_student_operations(
    user_sessions: Dict[int, Session],
    operation: Callable,
    max_workers: int = 10,
    *args,
    **kwargs,
) -> Dict[int, Any]:
    """
    Utility function to run an operation in parallel for all authenticated students.

    Args:
        user_sessions: Dictionary mapping user indices to their authenticated sessions
        operation: The function to execute for each student (first parameter should be session)
        max_workers: Maximum number of concurrent threads
        *args, **kwargs: Additional arguments to pass to the operation function

    Returns:
        Dictionary mapping user indices to their operation results
    """
    results = {}

    logging.info(f"Starting parallel operation for {len(user_sessions)} students...")
    with ThreadPoolExecutor(max_workers=max_workers) as executor:
        future_to_user = {
            executor.submit(
                run_operation_for_student,
                user_index,
                session,
                operation,
                *args,
                **kwargs,
            ): user_index
            for user_index, session in user_sessions.items()
        }

        for future in as_completed(future_to_user):
            user_index, result = future.result()
            results[user_index] = result
            if result is not None:
                logging.debug(f"Operation successful for user {user_index}")
            else:
                logging.error(f"Operation failed for user {user_index}")

    successful_operations = sum(1 for result in results.values() if result is not None)
    logging.info(
        f"Parallel operation completed. {successful_operations}/{len(user_sessions)} operations successful."
    )

    return results


def poll_job_completions(
    session: Session,
    exercise_id: int,
    course_id: int,
    timeout_seconds: int = 600,
    interval_seconds: int = 10,
) -> List[Dict[str, Any]]:
    logging.info(f"Polling job completions for exercise {exercise_id}...")
    start_time = time.time()

    logging.info("Waiting for submissions to be created...")
    while time.time() - start_time < timeout_seconds:
        submissions = get_submissions_for_exercise(session, exercise_id)
        if len(submissions) > 0:
            logging.info(
                f"Found {len(submissions)} submissions, starting to poll for results..."
            )
            break
        logging.debug("No submissions found yet, waiting...")
        time.sleep(interval_seconds)

    while time.time() - start_time < timeout_seconds:
        submissions = get_submissions_for_exercise(session, exercise_id)
        results = get_all_results(submissions)
        logging.info(
            f"Current submissions: {len(submissions)}, current results: {len(results)}"
        )

        running_jobs = get_running_build_jobs_for_course(session, course_id)
        logging.info(
            f"Currently running jobs for course {course_id}: {len(running_jobs)}"
        )

        queued_jobs = get_queued_build_jobs_for_course(session, course_id)
        logging.info(
            f"Currently queued jobs for course {course_id}: {len(queued_jobs)}"
        )

        log_build_agent_summaries(session)

        if len(results) == len(submissions):
            logging.info(
                f"All submissions have results. Returning {len(results)} results."
            )
            return results
        time.sleep(interval_seconds)

    submissions = get_submissions_for_exercise(session, exercise_id)
    results = get_all_results(submissions)
    logging.warning(
        f"Timeout reached after {timeout_seconds} seconds. Returning {len(results)} results for {len(submissions)} submissions."
    )
    return results


def create_course(session: Session, title: str, short_name: str) -> Dict[str, Any]:
    """Create a course using the provided session."""
    url = f"{SERVER_URL}/core/admin/courses"
    headers = {"Content-Type": "application/json"}
    course_data = {
        "title": title,
        "shortName": short_name,
    }
    fields = {"course": ("blob.json", json.dumps(course_data), "application/json")}

    body, content_type = urllib3.filepost.encode_multipart_formdata(fields)
    headers = {
        "Content-Type": content_type,
    }
    response = session.post(url, data=body, headers=headers)

    if response.status_code == 201:
        logging.info(
            f"Course created successfully with ID: {response.json().get('id')}"
        )
        return response.json()
    else:
        raise Exception(
            f"Could not create course; Status code: {response.status_code}\nResponse content: {response.text}"
        )

def register_students_to_course(session: Session, course_id: int, user_count: int):
    def register_user(user_index):
        add_user_to_course(
            session, course_id, "students", USER_NAME_PATTERN.format(user_index)
        )

    with ThreadPoolExecutor() as executor:
        executor.map(register_user, range(1, user_count + 1))


def main() -> None:
    number_of_students = 100
    number_of_commits = 1
    experiment_config = JAVA_FAILING_BUILD

    experiment_id = str(uuid4())
    logging.info(f"Starting experiment with ID: {experiment_id}")

    admin_session = Session()
    login_as_admin(admin_session)

    course_id = create_course(
        admin_session,
        f"Experiment Test Course {experiment_id}",
        f"course{experiment_id[:5]}",
    ).get("id")
    register_students_to_course(admin_session, course_id, number_of_students)
    logging.info(f"Using course: {course_id} for experiments")

    

    exercise = create_single_programming_exercise(
        admin_session,
        course_id,
        SERVER_URL,
        f"Test Experiment Exercise {experiment_id}",
        experiment_config.package_name,
        programming_language=experiment_config.programming_language,
        project_type=experiment_config.project_type,
        build_script=experiment_config.build_script,
    )
    programming_exercise_id = exercise.get("id")
    logging.info(
        f"Using programming exercise: {programming_exercise_id} for experiments. \n check: {CLIENT_URL}/course-management/{course_id}/exercises"
    )

    user_sessions = authenticate_users(
        number_of_students, max_workers=min(number_of_students, 10)
    )

    run_parallel_student_operations(
        user_sessions,
        participate_programming_exercise,
        exercise_id=programming_exercise_id,
        files_to_commit=experiment_config.commit_files,
        commits=number_of_commits,
    )

    results = poll_job_completions(
        admin_session,
        programming_exercise_id,
        course_id,
        timeout_seconds=60 * 20,
        interval_seconds=15,
    )

    logging.info(
        f"Results collected: {len(results)} results for {len(user_sessions)} students."
    )
    stats = get_build_job_statistics_for_course(admin_session, course_id)
    # finished_jobs = get_finished_build_jobs_for_course(admin_session, course_id)

    logging.info(f"Build job statistics for course {course_id}: {stats}")


if __name__ == "__main__":
    main()
