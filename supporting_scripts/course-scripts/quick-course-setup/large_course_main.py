import requests
import configparser
import logging
from requests import Session
from utils import authenticate_user
from create_course import create_course
from create_users import create_students, user_credentials
from add_users_to_course import add_students_to_groups_of_course
from manage_programming_exercise import create_programming_exercise, add_participation, commit, exercise_ids
from delete_students import delete_students

# Load configuration and constants
config = configparser.ConfigParser()
config.read('config.ini')

# Constants
STUDENTS_TO_CREATE: int = int(config.get('Settings', 'students')) + 1
COMMITS_PER_STUDENT: int = int(config.get('Settings', 'commits'))
EXERCISES_TO_CREATE: int = int(config.get('Settings', 'exercises'))

CLIENT_URL: str = config.get('Settings', 'client_url')
SERVER_URL: str = config.get('Settings', 'server_url')
ADMIN_USER: str = config.get('Settings', 'admin_user')
ADMIN_PASSWORD: str = config.get('Settings', 'admin_password')
COURSE_NAME: str = config.get('CourseSettings', 'course_name')
COURSE_ID: str = config.get('CourseSettings', 'course_id')
IS_LOCAL_COURSE: bool = config.get('CourseSettings', 'is_local_course').lower() == 'true'

def delete_all_created_students(session: Session) -> None:
    authenticate_user(ADMIN_USER, ADMIN_PASSWORD, session)
    delete_students(session, CLIENT_URL)
    logging.info(f"Deleted all created students successfully")

def main() -> None:
    # Step 1: Authenticate as admin
    session: Session = requests.session()
    authenticate_user(ADMIN_USER, ADMIN_PASSWORD, session)

    # Step 2: Create users
    create_students(session, STUDENTS_TO_CREATE)

    # Step 3: Create a course
    created_course_response = create_course(session)
    response_data = created_course_response.json()
    course_id: int = response_data["id"]

    # Step 4: Add users to the course
    add_students_to_groups_of_course(session, course_id, SERVER_URL, STUDENTS_TO_CREATE)

    # Step 5: Create programming exercises
    create_programming_exercise(session, course_id, SERVER_URL, EXERCISES_TO_CREATE)

    # Step 6: Add participation and commit for each user
    print("Created users and their credentials:")

    for username, password in user_credentials:
        user_session: Session = requests.Session()
        authenticate_user(username, password, user_session)

        for exercise_id in exercise_ids:
            participation_response = add_participation(user_session, exercise_id, CLIENT_URL)
            logging.info(f"Added participation for {username} in the programming exercise {exercise_id} successfully")
            participation_id: int = participation_response.get('id')

            commit(user_session, participation_id, CLIENT_URL, COMMITS_PER_STUDENT)
            logging.info(f"Added commit for {username} in the programming exercise {exercise_id} successfully")
            print(f"Participation and Commit done for user: {username}")

    # (Optional) Step 7 : Delete all created students
    # delete_all_created_students(session)

if __name__ == "__main__":
    main()
