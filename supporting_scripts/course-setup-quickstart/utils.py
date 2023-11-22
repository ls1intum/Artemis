import requests
import configparser
import logging

config = configparser.ConfigParser()
config.read('config.ini')

server_url = config.get('Settings', 'server_url')
admin_user = config.get('Settings', 'admin_user')
admin_password = config.get('Settings', 'admin_password')


def login_as_admin(session):
    authenticate_user(admin_user, admin_password, session)


def add_user_to_course(session, course_id, user_group, user_name):
    url = f"{server_url}/api/courses/{course_id}/{user_group}/{user_name}"
    response = session.post(url)
    if response.status_code == 200:
        logging.info(f"Added user {user_name} to group {user_group}")
    else:
        logging.error(f"Could not add user {user_name} to group {user_group}")


def authenticate_user(username, password, session=requests.Session()):
    url = f"{server_url}/api/public/authenticate"
    headers = {
        "Content-Type": "application/json"
    }

    payload = {
        "username": username,
        "password": password,
        "rememberMe": True
    }

    response = session.post(url, json=payload, headers=headers)

    if response.status_code == 200:
        logging.info(f"Authentication successful for user {username}")
    else:
        raise Exception(
            f"Authentication failed for user {username}. Status code: {response.status_code}\n Response content: {response.text}")

    return response


def get_user_details_by_index(user_index):
    username = f"artemis_test_user_{user_index}"
    password = username
    authorities = []
    groups = []

    user_role = "ROLE_USER"

    if 1 <= user_index <= 5 or user_index in {100, 102, 104, 105, 106}:
        authorities = [user_role]
        groups = ["students"]
    elif 6 <= user_index <= 10 or user_index == 101:
        authorities = [user_role, "ROLE_TA"]
        groups = ["tutors"]
    elif 11 <= user_index <= 15:
        authorities = [user_role, "ROLE_EDITOR"]
        groups = ["editors"]
    elif 16 <= user_index <= 20 or user_index == 103:
        authorities = [user_role, "ROLE_INSTRUCTOR"]
        groups = ["instructors"]

    return {
        "activated": True,
        "authorities": authorities,
        "login": username,
        "email": username + "@artemis.local",
        "firstName": username,
        "lastName": username,
        "langKey": "en",
        "guidedTourSettings": [],
        "groups": groups,
        "password": password
    }
