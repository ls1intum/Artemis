import requests
import configparser

config = configparser.ConfigParser()
config.read('config.ini')

server_url = config.get('Settings', 'server_url')
admin_user = config.get('Settings', 'admin_user')
admin_password = config.get('Settings', 'admin_password')


class Colors:
    # See https://stackoverflow.com/a/287944/16540383 if you want to
    # extend the colors and styling options for explanation
    SUCCESS = '\033[92m'
    ENDC = '\033[0m'


def print_success(success_message):
    print(f"{Colors.SUCCESS}{success_message}{Colors.ENDC}")


def login_as_admin(session):
    authenticate_user(admin_user, admin_password, session)


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
        print_success(f"Authentication successful for user {username}")
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
