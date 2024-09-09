import requests
import configparser
from logging_config import logging
from requests import Session

from utils import login_as_admin, get_user_details_by_index, add_user_to_course, get_student_details_by_index

# Load configuration
config = configparser.ConfigParser()
config.read('config.ini')

# Constants from config file
COURSE_ID: str = config.get('CourseSettings', 'course_id')

def add_users_to_groups_of_course(session: Session, course_id: str) -> None:
    print(f"Adding users to course with id {course_id}")
    for userIndex in range(1, 21):
        user_details = get_user_details_by_index(userIndex)
        add_user_to_course(session, course_id, user_details["groups"][0], user_details["login"])

def add_students_to_groups_of_course(session: Session, course_id: str, server_url: str, students_to_create: int) -> None:
    for user_index in range(1, students_to_create):
        user_details = get_student_details_by_index(user_index)
        add_students_to_course(session, course_id, user_details["groups"][0], user_details["login"], server_url)

def add_students_to_course(session: Session, course_id: str, user_group: str, user_name: str, server_url: str) -> None:
    url = f"{server_url}/courses/{course_id}/{user_group}/{user_name}"
    response = session.post(url)
    if response.status_code == 200:
        logging.info(f"Added user {user_name} to group {user_group}")
    else:
        logging.error(f"Could not add user {user_name} to group {user_group}")

def main() -> None:
    session = requests.session()
    login_as_admin(session)
    add_users_to_groups_of_course(session, COURSE_ID)

if __name__ == "__main__":
    # DO ONLY USE FOR LOCAL COURSE SETUP!
    # (Otherwise users will be created for whom the credentials are public in the repository!)
    main()
