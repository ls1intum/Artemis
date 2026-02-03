from concurrent.futures import ThreadPoolExecutor, as_completed
import configparser
import json
import subprocess
import os
import sys
import requests
from typing import Dict, List, Tuple
from logging_config import logging

# Load configuration
config = configparser.ConfigParser()
config.read(['config.ini'])

ADMIN_USER: str = config.get('Settings', 'admin_user')
ADMIN_PASSWORD: str = config.get('Settings', 'admin_password')
MAX_THREADS: int = config.getint('Settings', 'max_threads')
SERVER_URL: str = config.get('Settings', 'server_url')
CLIENT_URL: str = config.get('Settings', 'client_url')

SPECIAL_CHARACTER_REGEX: str = config.get('PECVCourseSettings', 'special_character_regex')
CREATE_COURSE: bool = config.getboolean('PECVCourseSettings', 'create_course')
COURSE_NAME: str = config.get('PECVCourseSettings', 'course_name')
IS_LOCAL_COURSE: bool = config.getboolean('PECVCourseSettings', 'is_local_course')
PECV_BENCH_FOLDER: str = config.get('PECVCourseSettings', 'pecv_bench_folder', fallback="pecv-bench")
PECV_BENCH_REPO: str = config.get('PECVCourseSettings', 'pecv_bench_repo', fallback="https://github.com/ls1intum/PECV-bench.git")

COURSE_EXERCISES: Dict[str, List[str]] = json.loads(config.get('PECVExerciseSettings', 'course_exercises'))

REFERENCE: str = config.get('PECVConsistencyCheckSettings', 'reference', fallback="No Data Available")

def login_as_admin(session: requests.Session) -> None:
    """
    Authenticate as an admin using the provided session.

    POST /core/public/authenticate

    :param requests.Session session: The session to authenticate.
    :return: None
    """
    authenticate_user(ADMIN_USER, ADMIN_PASSWORD, session)

def authenticate_user(username: str, password: str, session: requests.Session) -> requests.Response:
    """
    Authenticate a user and return the session response.

    :param str username: The username for authentication.
    :param str password: The password for authentication.
    :param requests.Session session: The session object to use for the request.
    :return: The response object from the authentication request.
    :rtype: requests.Response
    :raises Exception: If authentication fails (status code other than 200).
    """
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

# def get_pecv_bench_dir() -> str:
#     """
#     Gets the directory path for pecv-bench.

#     :return: The absolute path to the pecv-bench directory
#     :rtype: str
#     """
#     hyperion_benchmark_workflow_dir = os.path.dirname(os.path.abspath(__file__))
#     pecv_bench_dir = os.path.join(hyperion_benchmark_workflow_dir, PECV_BENCH_FOLDER)
#     return pecv_bench_dir