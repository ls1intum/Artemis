import requests
import configparser
from logging_config import logging
from requests import Session
from utils import authenticate_user
from create_course import create_course
from create_users import create_students, user_credentials
from add_users_to_course import add_students_to_groups_of_course
from manage_programming_exercise import create_programming_exercise, add_participation, commit, exercise_Ids
from randomize_results_after import run_cleanup

# Load configuration and constants
config = configparser.ConfigParser()
config.read('config.ini')

# Constants
STUDENTS_TO_CREATE: int = int(config.get('Settings', 'students')) + 1
COMMITS_PER_STUDENT: int = int(config.get('Settings', 'commits'))
EXERCISES_TO_CREATE: int = int(config.get('Settings', 'exercises'))
EXERCISES_NAME: str = str(config.get('Settings', 'exercise_name'))
CREATE_EXERCISES: bool = config.get('Settings', 'create_exercises').lower() == 'true'
EXERCISE_IDS: list[int] = list(map(int, config.get('Settings', 'exercise_Ids').split(',')))

CLIENT_URL: str = config.get('Settings', 'client_url')
SERVER_URL: str = config.get('Settings', 'server_url')
ADMIN_USER: str = config.get('Settings', 'admin_user')
ADMIN_PASSWORD: str = config.get('Settings', 'admin_password')
COURSE_NAME: str = config.get('CourseSettings', 'course_name')
COURSE_ID: str = config.get('CourseSettings', 'course_id')
CREATE_COURSE: bool = config.get('CourseSettings', 'create_course').lower() == 'true'
IS_LOCAL_COURSE: bool = config.get('CourseSettings', 'is_local_course').lower() == 'true'

def main() -> None:
    # Step 1: Authenticate as admin
    session: Session = requests.session()
    authenticate_user(ADMIN_USER, ADMIN_PASSWORD, session)

    # Step 2: Create users
    create_students(session, STUDENTS_TO_CREATE)

    # Step 3: Create a course or use an existing one
    if CREATE_COURSE:
        response_data = create_course(session)
        course_id: str = response_data["id"]
    else:
        course_id: str = str(COURSE_ID)

    # Step 4: Add users to the course
    add_students_to_groups_of_course(session, course_id, SERVER_URL, STUDENTS_TO_CREATE)

    # Step 5: Create programming exercises or use existing ones
    if CREATE_EXERCISES:
        create_programming_exercise(session, course_id, SERVER_URL, EXERCISES_TO_CREATE, EXERCISES_NAME)
    else:
        exercise_Ids.extend(EXERCISE_IDS)

    # Step 6: Add participation and commit for each user
    logging.info("Created users and their credentials:")

    for username, password in user_credentials:
        user_session: Session = requests.Session()
        authenticate_user(username, password, user_session)

        for exercise_Id in exercise_Ids:
            participation_response = add_participation(user_session, exercise_Id, CLIENT_URL)
            logging.info(f"Added participation for {username} in the programming exercise {exercise_Id} successfully")
            participation_id: int = participation_response.get('id')

            commit(user_session, participation_id, CLIENT_URL, COMMITS_PER_STUDENT)
            logging.info(f"Added commit for {username} in the programming exercise {exercise_Id} successfully")

    # This is a measure in case developers forget to revert changes to programming exercise template
    run_cleanup()

if __name__ == "__main__":
    main()
