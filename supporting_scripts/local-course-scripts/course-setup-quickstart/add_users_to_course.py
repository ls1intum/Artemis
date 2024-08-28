import requests
import configparser

from utils import login_as_admin
from utils import get_user_details_by_index
from utils import add_user_to_course

config = configparser.ConfigParser()
config.read('config.ini')

course_id = config.get('CourseSettings', 'course_id')


def add_users_to_groups_of_course(session, course_id):
    print(f"Adding users to course with id {course_id}")
    for userIndex in range(1, 21):
        user_details = get_user_details_by_index(userIndex)
        add_user_to_course(session, course_id, user_details["groups"][0], user_details["login"])


def main():
    session = requests.session()
    login_as_admin(session)
    add_users_to_groups_of_course(session, course_id)


if __name__ == "__main__":
    # DO ONLY USE FOR LOCAL COURSE SETUP!
    # (Otherwise users will be created for whom the credentials are public in the repository!)
    main()
