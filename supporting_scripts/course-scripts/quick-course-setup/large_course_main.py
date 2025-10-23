import requests
import configparser
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import Tuple
from requests import Session

from logging_config import logging
from utils import authenticate_user
from create_course import create_course
from create_users import create_students, user_credentials
from add_users_to_course import add_students_to_groups_of_course
from manage_programming_exercise import create_programming_exercise, add_participation, commit, exercise_Ids
from randomize_results_after import run_cleanup

# Load configuration and constants
config = configparser.ConfigParser()
config.read(['../config.ini', 'config.ini'])

# Constants
STUDENTS_TO_CREATE: int = int(config.get("Settings", "students")) + 1
COMMITS_PER_STUDENT: int = int(config.get("Settings", "commits"))
EXERCISES_TO_CREATE: int = int(config.get("Settings", "exercises"))
EXERCISES_NAME: str = str(config.get("Settings", "exercise_name"))
CREATE_EXERCISES: bool = config.get("Settings", "create_exercises").lower() == "true"
CLIENT_URL: str = config.get("Settings", "client_url")
SERVER_URL: str = config.get("Settings", "server_url")
ADMIN_USER: str = config.get("Settings", "admin_user")
ADMIN_PASSWORD: str = config.get("Settings", "admin_password")
COURSE_NAME: str = config.get("CourseSettings", "course_name")
COURSE_ID: str = config.get("CourseSettings", "course_id")
CREATE_COURSE: bool = config.get("CourseSettings", "create_course").lower() == "true"
IS_LOCAL_COURSE: bool = config.get("CourseSettings", "is_local_course").lower() == "true"

try:
    MAX_WORKERS: int = int(config.get("Settings", "max_threads"))
except (configparser.NoOptionError, configparser.NoSectionError, ValueError):
    MAX_WORKERS = 8


def process_user(user: Tuple[str, str]) -> None:
    """
    Worker for a single user:
    - Authenticate as the user
    - Add participation for all exercises
    - Do N commits per exercise
    """
    username, password = user
    try:
        user_session: Session = requests.Session()
        authenticate_user(username, password, user_session)
        for exercise_id in exercise_Ids:
            participation_response = add_participation(user_session, exercise_id, CLIENT_URL)
            participation_id: int = participation_response.get("id")
            logging.info(
                f"[{username}] Added participation in programming exercise {exercise_id} (participation_id={participation_id})"
            )

            commit(user_session, participation_id, CLIENT_URL, COMMITS_PER_STUDENT)
            logging.info(
                f"[{username}] Added {COMMITS_PER_STUDENT} commits in programming exercise {exercise_id} successfully"
            )
    except Exception as e:
        logging.exception(f"[{username}] Error during participation/commit flow: {e}")


def main() -> None:
    # Step 1: Authenticate as admin
    admin_session: Session = requests.session()
    authenticate_user(ADMIN_USER, ADMIN_PASSWORD, admin_session)

    # Step 2: Create users
    create_students(admin_session, STUDENTS_TO_CREATE)

    # Step 3: Create a course or use an existing one
    if CREATE_COURSE:
        response_data = create_course(admin_session)
        course_id: str = response_data["id"]
    else:
        course_id: str = str(COURSE_ID)

    # Step 4: Add users to the course
    add_students_to_groups_of_course(admin_session, course_id, SERVER_URL, STUDENTS_TO_CREATE)

    # Step 5: Create programming exercises or use existing ones
    if CREATE_EXERCISES:
        create_programming_exercise(
            admin_session, course_id, SERVER_URL, EXERCISES_TO_CREATE, EXERCISES_NAME
        )
    else:
        ids_raw = config.get("Settings", "exercise_Ids", fallback="")
        ids = [int(x) for x in ids_raw.split(",") if x.strip()]
        if not ids:
            logging.warning("No exercise IDs provided in config; nothing to process.")
            return
        exercise_Ids.extend(ids)
    # Step 6: Add participation and commit for each user (in parallel)
    logging.info("Created users and their credentials:")
    if not user_credentials:
        logging.warning("No user credentials found to process.")
        return

    logging.info(f"Starting threaded execution with max_workers={MAX_WORKERS}...")
    with ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
        futures = [executor.submit(process_user, creds) for creds in user_credentials]

        for i, future in enumerate(as_completed(futures), start=1):
            try:
                future.result()
            except Exception as e:
                logging.exception(f"[Thread-{i}] Unhandled exception: {e}")

    # This should be uncommented if randomize_results script is being used by the developer!
    # run_cleanup()


if __name__ == "__main__":
    main()
