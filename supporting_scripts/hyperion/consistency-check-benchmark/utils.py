import configparser
import json
import os
import sys
import requests
from typing import Any, Dict, List
from logging_config import logging

# Load configuration
config = configparser.ConfigParser()
if not os.path.exists('config.ini'):
    logging.critical("Configuration file 'config.ini' not found. Please ensure it exists in the working directory.")
    sys.exit(1)

config.read(['config.ini'])

try:
    ADMIN_USER: str = config.get('Settings', 'admin_user')
    ADMIN_PASSWORD: str = config.get('Settings', 'admin_password')
    MAX_THREADS: int = config.getint('Settings', 'max_threads')
    SERVER_URL: str = config.get('Settings', 'server_url')
    CLIENT_URL: str = config.get('Settings', 'client_url')

    SPECIAL_CHARACTER_REGEX: str = config.get('PECVCourseSettings', 'special_character_regex')
    CREATE_COURSE: bool = config.getboolean('PECVCourseSettings', 'create_course')
    COURSE_NAME: str = config.get('PECVCourseSettings', 'course_name')
    IS_LOCAL_COURSE: bool = config.getboolean('PECVCourseSettings', 'is_local_course')
    PECV_BENCH_DIR: str = config.get('PECVCourseSettings', 'pecv_bench_dir', fallback="pecv-bench")
    PECV_BENCH_URL: str = config.get('PECVCourseSettings', 'pecv_bench_url', fallback="https://github.com/ls1intum/PECV-bench.git")
    PECV_BENCH_BRANCH: str = config.get('PECVCourseSettings', 'pecv_bench_branch', fallback="main")
    PECV_BENCH_DATASET_DIR: str = config.get('PECVCourseSettings', 'pecv_bench_dataset_dir', fallback="pecv-bench-dataset")
    PECV_BENCH_DATASET_URL: str = config.get('PECVCourseSettings', 'pecv_bench_dataset_url', fallback="https://github.com/ls1intum/PECV-bench-dataset.git")

    DATASET_VERSION: str = config.get('PECVExerciseSettings', 'dataset_version', fallback="V1")
    COURSE_EXERCISES: Dict[str, Dict[str, List[str]]] = json.loads(config.get('PECVExerciseSettings', 'course_exercises'))

    MODEL_NAME: str = config.get('PECVConsistencyCheckSettings', 'model_name', fallback="azure-openai-gpt-5-mini")
    MODEL_EFFORT: str = config.get('PECVConsistencyCheckSettings', 'model_effort', fallback="medium")
    CONSISTENCY_CHECK_EXERCISES: Dict[str, Dict[str, List[str]]] = json.loads(config.get('PECVConsistencyCheckSettings', 'consistency_check_exercises', fallback='{}'))
    CODE_SNAPSHOT_FILES: Dict[str, List[str]] = json.loads(config.get('PECVConsistencyCheckSettings', 'code_snapshot_files', fallback='{}'))
    REFERENCE: Dict[str, str] = json.loads(config.get('PECVConsistencyCheckSettings', 'reference', fallback='{}'))
except (configparser.Error, json.JSONDecodeError, ValueError) as e:
    logging.critical(f"Error loading configuration: {e}")
    sys.exit(1)

def login_as_admin(session: requests.Session) -> None:
    """
    Authenticate as an admin using the provided session.

    POST /core/public/authenticate

    :param requests.Session session: The session to authenticate.
    :return: None
    :rtype: None
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
        logging.error(f"Step 6 failed: Authentication failed for user {username}. Status code: {response.status_code}\nResponse content: {response.text}")
        logging.error(f"Check admin_user and admin_password in config.ini, verify the server is running at {SERVER_URL}, then execute Step 6")
        raise Exception(f"Step 6 failed: Authentication failed for user {username}. Status code: {response.status_code}")

    return response