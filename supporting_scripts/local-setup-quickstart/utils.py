import requests
import configparser

config = configparser.ConfigParser()
config.read('config.ini')

backend_url = config.get('Settings', 'backend_url')
admin_user = config.get('Settings', 'admin_user')
admin_password = config.get('Settings', 'admin_password')


class Colors:
    # See https://stackoverflow.com/a/287944/16540383 if you want to
    # extend the colors and styling options for explanation
    SUCCESS = '\033[92m'
    ERROR = '\033[91m'


def print_error(error_message):
    print(f"{Colors.ERROR}{error_message}{Colors.ERROR}")


def print_success(success_message):
    print(f"{Colors.SUCCESS}{success_message}{Colors.SUCCESS}")


def login_as_admin(session):
    authenticate_user(admin_user, admin_password, session)


def authenticate_user(username, password, session=requests.Session()):
    url = f"{backend_url}/api/public/authenticate"
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
        print_success(f"Authentication successful for user {username}")
    else:
        print_error(f"Authentication failed for user {username}. Status code: {response.status_code}")
        print_error(f"Response content: {response.text}")

    return response


def get_user_details_by_index(user_index):
    username = f"artemis_test_user_{user_index}"
    password = username
    authorities = []
    groups = []

    if 1 <= user_index <= 5 or user_index in {100, 102, 104, 106}:
        authorities = ["ROLE_USER"]
        groups = ["students"]
    elif 6 <= user_index <= 10 or user_index == 101:
        authorities = ["ROLE_TA"]
        groups = ["tutors"]
    elif 11 <= user_index <= 15:
        authorities = ["ROLE_EDITOR"]
        groups = ["editors"]
    elif 16 <= user_index <= 20 or user_index == 103:
        authorities = ["ROLE_INSTRUCTOR"]
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
