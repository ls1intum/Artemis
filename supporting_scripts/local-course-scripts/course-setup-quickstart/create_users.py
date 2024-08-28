import requests
import configparser
import logging

from authenticate_all_users import authenticate_all_generated_users
from utils import login_as_admin
from utils import get_user_details_by_index

config = configparser.ConfigParser()
config.read('config.ini')

client_url = config.get('Settings', 'client_url')


def make_create_user_post_request(session, user_details):
    url = f"{client_url}/api/admin/users"
    headers = {
        "Content-Type": "application/json"
    }
    payload = user_details
    response = session.post(url, json=payload, headers=headers)

    if response.status_code == 201:
        logging.info(f"{user_details['login']} was created successfully")
    elif response.status_code == 400 and "userExists" in response.json().get("errorKey", ""):
        logging.info(f"User {user_details['login']} already exists.")
    else:
        raise Exception(
            f"Creating {user_details['login']} failed. Status code: {response.status_code}\nResponse content: {response.text}")


def create_course_users(session):
    for userIndex in range(1, 21):
        user_details = get_user_details_by_index(userIndex)
        make_create_user_post_request(session, user_details)


def create_cypress_users(session):
    for userIndex in range(100, 107):
        user_details = get_user_details_by_index(userIndex)
        make_create_user_post_request(session, user_details)


def create_users(session):
    create_course_users(session)
    create_cypress_users(session)


def main():
    session = requests.session()
    login_as_admin(session)

    try:
        create_users(session)
    except Exception as exception:
        error_message = str(exception)

        if "Login name already used!" in error_message:
            print("Users already created. Continuing ...")
        else:
            raise

    create_users(session)
    authenticate_all_generated_users()


if __name__ == "__main__":
    # DO ONLY USE FOR LOCAL COURSE SETUP!
    # (Otherwise users will be created for whom the credentials are public in the repository!)
    main()
