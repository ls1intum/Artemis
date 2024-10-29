import requests
import configparser
from logging_config import logging
from typing import Dict, Any, List

config = configparser.ConfigParser()
config.read('config.ini')

SERVER_URL: str = config.get('Settings', 'server_url')
ADMIN_USER: str = config.get('Settings', 'admin_user')
ADMIN_PASSWORD: str = config.get('Settings', 'admin_password')

def login_as_admin(session: requests.Session) -> None:
    """Authenticate as an admin using the provided session."""
    authenticate_user(ADMIN_USER, ADMIN_PASSWORD, session)

def add_user_to_course(session: requests.Session, course_id: int, user_group: str, user_name: str) -> None:
    """Add a user to a specified course and group."""
    url: str = f"{SERVER_URL}/courses/{course_id}/{user_group}/{user_name}"
    response: requests.Response = session.post(url)
    if response.status_code == 200:
        logging.info(f"Added user {user_name} to group {user_group}")
    else:
        logging.error(f"Could not add user {user_name} to group {user_group}")

def authenticate_user(username: str, password: str, session: requests.Session = requests.Session()) -> requests.Response:
    """Authenticate a user and return the session response."""
    url: str = f"{SERVER_URL}/public/authenticate"
    headers: Dict[str, str] = {
        "Content-Type": "application/json"
    }

    payload: Dict[str, Any] = {
        "username": username,
        "password": password,
        "rememberMe": True
    }

    response: requests.Response = session.post(url, json=payload, headers=headers)

    if response.status_code == 200:
        logging.info(f"Authentication successful for user {username}")
    else:
        raise Exception(
            f"Authentication failed for user {username}. Status code: {response.status_code}\n Response content: {response.text}")

    return response

def get_user_details_by_index(user_index: int) -> Dict[str, Any]:
    """Generate user details based on the index for predefined users."""
    username: str = f"artemis_test_user_{user_index}"
    password: str = username
    authorities: List[str] = []
    groups: List[str] = []

    user_role: str = "ROLE_USER"

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
        "email": f"{username}@artemis.local",
        "firstName": username,
        "lastName": username,
        "langKey": "en",
        "guidedTourSettings": [],
        "groups": groups,
        "password": password
    }

def get_student_details_by_index(user_index: int) -> Dict[str, Any]:
    """Generate user details based on the index for students."""
    username: str = f"student{user_index}"
    password: str = "Password123!"
    user_role: str = "ROLE_USER"
    authorities: List[str] = [user_role]
    groups: List[str] = ["students"]

    return {
        "activated": True,
        "authorities": authorities,
        "login": username,
        "email": f"{username}@example.com",
        "firstName": "Test",
        "lastName": f"User{user_index}",
        "langKey": "en",
        "guidedTourSettings": [],
        "groups": groups,
        "password": password
    }
