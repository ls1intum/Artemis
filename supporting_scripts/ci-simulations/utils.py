import requests
import configparser
from logging_config import logging
from typing import Dict, Any, List

config = configparser.ConfigParser()
config.read('config.ini')


def add_user_to_course(session: requests.Session, course_id: int, user_group: str, user_name: str, server_url: str) -> None:
    """Add a user to a specified course and group."""
    url: str = f"{server_url}/core/courses/{course_id}/{user_group}/{user_name}"
    response: requests.Response = session.post(url)
    if response.status_code == 200:
        logging.debug(f"Added user {user_name} to group {user_group}")
    else:
        logging.error(f"Could not add user {user_name} to group {user_group}")

def authenticate_user(username: str, password: str, session: requests.Session, server_url: str) -> requests.Response:
    """Authenticate a user and return the session response."""
    url: str = f"{server_url}/core/public/authenticate"
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
        logging.debug(f"Authentication successful for user {username}")
    else:
        raise Exception(
            f"Authentication failed for user {username}. Status code: {response.status_code}\n Response content: {response.text}")

    return response