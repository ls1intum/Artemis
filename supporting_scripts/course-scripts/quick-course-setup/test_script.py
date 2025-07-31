from uuid import uuid4
from requests import Session
import time
import configparser
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import Dict, Callable, Any, List, Tuple
from logging_config import logging
from utils import login_as_admin, authenticate_user
from add_users_to_course import add_users_to_groups_of_course
from manage_programming_exercise import create_programming_exercise, create_single_programming_exercise
from create_course import create_course

# Load configuration
config = configparser.ConfigParser()
config.read('config.ini')

# Constants from config file
SERVER_URL: str = config.get('Settings', 'server_url')
COURSE_ID: str = config.get('CourseSettings', 'course_id')

infinite_build_script = """#!/usr/bin/env bash
        set -e
        main () {
        while true; do
            echo '✅ Loop cycle complete, restarting...'
            sleep 1
        done
        }
        main "${@}"
    """

failing_build_script = """#!/usr/bin/env bash
        set -e
        echo "❌ This script is designed to fail immediately."
        exit 1
    """

spammy_build_script = """#!/usr/bin/env bash
        set -e
        main () {
            while true; do
            for i in {1..1000}; do
                echo "📣 Log line $i: This is a test log message meant to spam the output."
            done
            echo "🔁 Completed 1000 log lines. Restarting..."
            sleep 0.1
            done
        }
        main "${@}"
    """

def authenticate_single_user(user_index: int) -> tuple[int, Session]:
    """Authenticate a single user and return the session."""
    session = Session()
    username = f"artemis_test_user_{user_index}"
    password = f"artemis_test_user_{user_index}"

    try:
        authenticate_user(username, password, session)
        return user_index, session
    except Exception as e:
        logging.error(f"Failed to authenticate user {username}: {e}")
        return user_index, None

def participate_programming_exercise(session: Session, exercise_id: str, commits: int = 1):
    """Make a student participate in a programming exercise."""
    participation_url = f"{SERVER_URL}/exercise/exercises/{exercise_id}/participations"
    participation_response = session.post(participation_url)
    if participation_response.status_code != 201:
        logging.error(f"Failed to create participation for student. Status: {participation_response.status_code}")
        return

    participation = participation_response.json()
    participation_id = participation.get('id')

    for _ in range(0, commits):
        commit_url = f"{SERVER_URL}/programming/repository/{participation_id}/commit"
        commit_response = session.post(commit_url)
        if commit_response.status_code not in [200, 201]:
            logging.error(f"Failed to commit for student. Status: {commit_response.status_code}")
            return
        logging.debug(f"Student successfully committed exercise {exercise_id}")
    return participation

def run_operation_for_student(user_index: int, session: Session, operation: Callable, *args, **kwargs) -> Tuple[int, Any]:
    """Execute an operation for a single student and return the result."""
    try:
        result = operation(session, *args, **kwargs)
        logging.debug(f"Operation completed successfully for user {user_index}")
        return user_index, result
    except Exception as e:
        logging.error(f"Operation failed for user {user_index}: {e}")
        return user_index, None

def run_parallel_student_operations(user_sessions: Dict[int, Session],
                                  operation: Callable,
                                  max_workers: int = 5,
                                  *args, **kwargs) -> Dict[int, Any]:
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
        # Submit all operation tasks
        future_to_user = {
            executor.submit(run_operation_for_student, user_index, session, operation, *args, **kwargs): user_index
            for user_index, session in user_sessions.items()
        }

        # Collect results as they complete
        for future in as_completed(future_to_user):
            user_index, result = future.result()
            results[user_index] = result
            if result is not None:
                logging.debug(f"Operation successful for user {user_index}")
            else:
                logging.error(f"Operation failed for user {user_index}")

    successful_operations = sum(1 for result in results.values() if result is not None)
    logging.info(f"Parallel operation completed. {successful_operations}/{len(user_sessions)} operations successful.")

    return results


def get_submissions_for_exercise(session: Session, exercise_id: int):
    participations = get_participations(session, exercise_id)
    submissions = []
    for participation in participations:
        submissions.extend(get_submissions(session, participation["id"]))
    return submissions

def get_participations(session: Session, exercise_id: int):
    participation_url = f"{SERVER_URL}/exercise/exercises/{exercise_id}/participations"
    response = session.get(participation_url)
    if response.status_code != 200:
        logging.error(f"Failed to get participations for exercise {exercise_id}, {response.text}")
    return response.json()

def get_submissions(session: Session, participation_id: int):
    submissions_url = f"{SERVER_URL}/exercise/participations/{participation_id}/submissions"
    response = session.get(submissions_url)
    if response.status_code != 200:
        logging.error(f"Failed to get submissions for participation {participation_id}, {response.text}")
    return response.json()

def get_results(submission: Dict[str, Any]) -> List[Dict[str, Any]]:
    """Extract results from a submission."""
    results = []
    if 'results' in submission and submission['results']:
        results.extend(submission['results'])
    return results

def get_all_results(submissions: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """Extract results from submissions."""
    results = []
    for submission in submissions:
        results.extend(get_results(submission))
    return results

def authenticate_users(user_count: int, max_workers = 4) -> Dict[int, Session]:
    user_sessions = {}
    test_user_range = range(1, user_count + 1)
    with ThreadPoolExecutor(max_workers=max_workers) as executor:
        future_to_user = {
            executor.submit(authenticate_single_user, i): i
            for i in test_user_range
        }

        for future in as_completed(future_to_user):
            user_index, session = future.result()
            if session is not None:
                user_sessions[user_index] = session
            else:
                logging.error(f"Failed to store session for user {user_index}")

    logging.info(f"Parallel authentication completed. {len(user_sessions)} users authenticated successfully.")
    return user_sessions

def poll_job_completions(session: Session, exercise_id: int, timeout_seconds: int = 600, interval_seconds: int = 10) -> List[Dict[str, Any]]:
    logging.info(f"Polling job completions for exercise {exercise_id}...")
    start_time = time.time()
    
    while time.time() - start_time < timeout_seconds:
        submissions = get_submissions_for_exercise(session, exercise_id)
        results = get_all_results(submissions)
        logging.info(f"Current submissions: {len(submissions)}, current results: {len(results)}")
        log_build_agent_summaries(session)
        if len(results) >= len(submissions) and len(submissions) > 0:
            logging.info(f"All submissions have results. Returning {len(results)} results.")
            return results
        time.sleep(interval_seconds)
    
    submissions = get_submissions_for_exercise(session, exercise_id)
    results = get_all_results(submissions)
    logging.warning(f"Timeout reached after {timeout_seconds} seconds. Returning {len(results)} results for {len(submissions)} submissions.")
    return results

def log_build_agent_summaries(session: Session): 
    build_agents = get_build_agents(session)
    for agent in build_agents:
        logging.debug(get_build_agent_summary_str(agent))

def get_build_agent_summary_str(build_agent: Dict[str, Any]) -> str:
   return f"Build Agent {build_agent.get('buildAgent').get('displayName')} - Status: {build_agent.get('status', 'Unknown')} - Currently processing {build_agent.get('numberOfCurrentBuildJobs', 0)} jobs"

def get_build_agents(session: Session) -> List[Dict[str, Any]]:
    """Get the list of build agents."""
    url = f"{SERVER_URL}/core/admin/build-agents"
    response = session.get(url)
    if response.status_code != 200:
        logging.error(f"Failed to get build agents: {response.text}")
        return []
    return response.json()

def get_build_agents_with_status(build_agents: List[Dict[str, Any]], status: str) -> List[Dict[str, Any]]:
    """Filter build agents by their status."""
    filtered_agents = [agent for agent in build_agents if agent.get('status') == status]
    logging.debug(f"Found {len(filtered_agents)} build agents with status '{status}'.")
    return filtered_agents

def main() -> None:
    admin_session = Session()
    login_as_admin(admin_session)
    logging.info(f"Using course: {COURSE_ID} for experiments")
    exercise = create_single_programming_exercise(admin_session, COURSE_ID, SERVER_URL, "Test Experiment Exercise" + str(uuid4())) # failing_build_script
    programming_exercise_id = exercise.get("id")
    logging.debug("Starting parallel authentication of users...")
    user_sessions = authenticate_users(2, max_workers=5)

    run_parallel_student_operations(user_sessions, participate_programming_exercise, exercise_id=programming_exercise_id, commits=2)
    results = poll_job_completions(admin_session, programming_exercise_id)
    
if __name__ == "__main__":
    main()
