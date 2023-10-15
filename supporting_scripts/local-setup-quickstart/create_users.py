import requests
import configparser

from authenticate_all_users import authenticate_all_generated_users
from utils import login_as_admin
from utils import get_user_details_by_index
from utils import print_success

config = configparser.ConfigParser()
config.read('config.ini')

client_url = config.get('Settings', 'client_url')


def make_create_user_post_request(session, user_details):
    url = f"http://localhost:9000/api/public/authenticate"
    headers = {
        "Content-Type": "application/json"
    }
    payload = user_details
    response = session.post(url, json=payload, headers=headers)

    if response.status_code == 201:
        print_success(f"{user_details['login']} was created successfully")
    else:
        raise Exception(
            f"Creating {user_details['login']} failed. Status code: {response.status_code}\nResponse content: {response.text}")


def create_course_users(session):
    for userIndex in range(1, 21):
        user_details = get_user_details_by_index(userIndex)
        make_create_user_post_request(session, user_details)


def create_cypress_users(session):
    for userIndex in range(100, 104):
        user_details = get_user_details_by_index(userIndex)
        make_create_user_post_request(session, user_details)

    user_details = get_user_details_by_index(106)
    make_create_user_post_request(session, user_details)


def create_users(session):
    create_course_users(session)
    create_cypress_users(session)


def main():
    session = requests.session()
    login_as_admin(session)

    create_users(session)
    authenticate_all_generated_users()


if __name__ == "__main__":
    main()
