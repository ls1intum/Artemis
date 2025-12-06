import requests
import configparser
from logging_config import logging
from typing import Dict, Any

config = configparser.ConfigParser()
config.read(['config.ini'])

CLIENT_URL: str = config.get('Settings', 'client_url')
SERVER_URL: str = config.get('Settings', 'server_url')
ADMIN_USER: str = config.get('Settings', 'admin_user')
ADMIN_PASSWORD: str = config.get('Settings', 'admin_password')

def login_as_admin(session: requests.Session) -> None:
    """Authenticate as an admin using the provided session."""
    authenticate_user(ADMIN_USER, ADMIN_PASSWORD, session)

def authenticate_user(username: str, password: str, session: requests.Session = requests.Session()) -> requests.Response:
    """Authenticate a user and return the session response."""
    url: str = f"{SERVER_URL}/core/public/authenticate"
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


