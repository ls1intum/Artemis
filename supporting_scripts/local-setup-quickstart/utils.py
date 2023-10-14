import requests
import configparser

config = configparser.ConfigParser()
config.read('config.ini')

backend_url = config.get('Settings', 'backend_url')
admin_user = config.get('Settings', 'admin_user')
admin_password = config.get('Settings', 'admin_password')


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
        print(f"Authentication successful for user {username}")
    else:
        print(f"Authentication failed for user {username}. Status code: {response.status_code}")
        print(f"Response content: {response.text}")

    return response


def get_user_details_by_index(user_index):
    username = f"artemis_test_user_{user_index}"
    password = username
    authorities = []
    groups = []

    if 1 <= user_index <= 5:
        authorities = ["ROLE_USER"]
        groups = ["students"]
    elif 6 <= user_index <= 10:
        authorities = ["ROLE_TA"]
        groups = ["tutors"]
    elif 11 <= user_index <= 15:
        authorities = ["ROLE_EDITOR"]
        groups = ["editors"]
    elif 16 <= user_index <= 20:
        authorities = ["ROLE_INSTRUCTOR"]
        groups = ["instructors"]
    # the following users are test users needed for executing the cypress tests
    elif user_index in {100, 102, 104, 106}:
        authorities = ["ROLE_USER"]
        groups = ["students"]
    elif user_index == 103:
        authorities = ["ROLE_INSTRUCTOR"]
        groups = ["instructors"]
    elif user_index == 101:
        authorities = ["ROLE_TA"]
        groups = ["tutors"]

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
