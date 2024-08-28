import requests
import configparser
import logging
from login import authenticate_user
from createCourse import create_course
from createStudents import create_users, user_credentials
from manageCourse import add_users_to_groups_of_course
from manageProgrammingExercise import create_programming_exercise, add_participation, commit
from deleteStudents import delete_users
# Load configuration and constants
config = configparser.ConfigParser()
config.read('config.ini')

# Constants
STUDENTS_TO_CREATE = int(config.get('Settings', 'students')) + 1
COMMITS_PER_STUDENT = int(config.get('Settings', 'commits'))

CLIENT_URL = config.get('Settings', 'client_url')
SERVER_URL = config.get('Settings', 'server_url')
ADMIN_USER = config.get('Settings', 'admin_user')
ADMIN_PASSWORD = config.get('Settings', 'admin_password')
SPECIAL_CHARACTERS_REGEX = r'[^a-zA-Z0-9_]'
COURSE_NAME = config.get('CourseSettings', 'course_name')
COURSE_ID = config.get('CourseSettings', 'course_id')
IS_LOCAL_COURSE = config.get('CourseSettings', 'is_local_course').lower() == 'true'

def delete_all_created_students(session):
    authenticate_user(ADMIN_USER, ADMIN_PASSWORD, SERVER_URL, session)
    delete_users(session, CLIENT_URL)
    logging.info(f"Deleted all created Students successfully")

def main():
    # Step 1: Authenticate as admin
    session = requests.session()
    authenticate_user(ADMIN_USER, ADMIN_PASSWORD, SERVER_URL, session)

    # Step 2: Create users
    try:
        create_users(session, CLIENT_URL, STUDENTS_TO_CREATE)
    except Exception as exception:
        error_message = str(exception)
        if "Login name already used!" in error_message:
            print("Users already created. Continuing ...")
        else:
            raise

    # Step 3: Create a course
    created_course_response = create_course(session, SERVER_URL, COURSE_NAME, IS_LOCAL_COURSE, SPECIAL_CHARACTERS_REGEX)
    response_data = created_course_response.json()
    course_id = response_data["id"]

    # Step 4: Add users to the course
    add_users_to_groups_of_course(session, course_id, SERVER_URL, STUDENTS_TO_CREATE)

    # Step 5: Create a programming exercise
    response_data = create_programming_exercise(session, course_id, SERVER_URL).json()
    exercise_id = response_data.get('id')

    # Step 6: Add participation and commit for each user
    print("Created users and their credentials:")

    for username, password in user_credentials:
        user_session = requests.Session()
        authenticate_user(username, password, SERVER_URL, user_session)


        participation_response = add_participation(user_session, exercise_id, CLIENT_URL)
        logging.info(f"Added participation for {username} in the programming exercise {exercise_id} successfully")
        participation_id = participation_response.get('id')

        for _ in range(COMMITS_PER_STUDENT):
            commit(user_session, participation_id, CLIENT_URL)
            logging.info(f"Added commit for {username} in the programming exercise {exercise_id} successfully")
        print(f"Participation and Commit done for user: {username}")

    # (Optional) Step 7 : Delete all created students
    # delete_all_created_students(session)

if __name__ == "__main__":
    main()
