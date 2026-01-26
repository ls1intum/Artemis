from concurrent.futures import ThreadPoolExecutor, as_completed
import configparser
import json
import subprocess
import os
import sys
import requests
from typing import Dict, List, Tuple
from logging_config import logging
from manage_programming_exercise import convert_variant_to_zip, import_programming_exercise_request

# Load configuration
config = configparser.ConfigParser()
config.read(['config.ini'])

PECV_BENCH_PATH: str = config.get('PECVBenchSettings', 'pecv_bench_folder', fallback="pecv-bench")
PECV_BENCH_REPO_URL: str = config.get('PECVBenchSettings', 'pecv_bench_repo', fallback="https://github.com/ls1intum/PECV-bench.git")

course_exercises_raw = config.get('PECVBenchSettings', 'course_exercises', fallback='{}')
COURSE_EXERCISES: Dict[str, List[str]] = json.loads(course_exercises_raw)

MAX_THREADS: int = int(config.get('Settings', 'max_threads', fallback="5"))
REFERENCE: str = config.get('PECVBenchSettings', 'reference', fallback="No Data Available")


CLIENT_URL: str = config.get('Settings', 'client_url')
SERVER_URL: str = config.get('Settings', 'server_url')
ADMIN_USER: str = config.get('Settings', 'admin_user')
ADMIN_PASSWORD: str = config.get('Settings', 'admin_password')

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

def get_pecv_bench_dir() -> str:
    """
    Gets the directory path for pecv-bench.

    :return: The absolute path to the pecv-bench directory
    :rtype: str
    """
    hyperion_benchmark_workflow_dir = os.path.dirname(os.path.abspath(__file__))
    pecv_bench_dir = os.path.join(hyperion_benchmark_workflow_dir, PECV_BENCH_PATH)
    return pecv_bench_dir

def clone_pecv_bench(pecv_bench_url: str, pecv_bench_dir: str) -> None:
    """
    Clones a repository if it doesn't exist, or pulls updates if it does.

    :param str pecv_bench_url: The URL of the repository to clone
    :param str pecv_bench_dir: The directory where the repository should be cloned
    :raises SystemExit: if the git command fails
    """

    if os.path.exists(pecv_bench_dir):
        logging.info(f"Directory {PECV_BENCH_PATH} already exists. Pulling latest changes.")
        try:
            subprocess.run(
                ["git","reset", "--hard", "HEAD"],
                cwd=pecv_bench_dir,
                check=True)
            subprocess.run(
                ["git", "clean", "-fd"],
                cwd=pecv_bench_dir,
                check=True)
            subprocess.run(
                ["git", "checkout", "dataset-extension"],
                cwd=pecv_bench_dir,
                check=True,
            )
            logging.info("Successfully checkout to latest dataset.")
            subprocess.run(
                ["git", "pull"],
                cwd=pecv_bench_dir,
                check=True,
            )
            logging.info("Successfully pulled latest changes.")

        except subprocess.CalledProcessError as e:
            logging.error(f"ERROR: Failed to pull updates for {pecv_bench_dir}.")
            logging.error(f"Stderr: {e.stderr}")
            sys.exit(1)
    else:
        logging.info(f"Cloning repository from {pecv_bench_url} into {pecv_bench_dir}.")
        try:
            subprocess.run(
                ["git", "clone", pecv_bench_url, pecv_bench_dir],
                check=True,
            )
            logging.info("Successfully cloned the repository.")

            subprocess.run(
                ["git", "checkout", "dataset-extension"],
                cwd=pecv_bench_dir,
                check=True,
            )
            logging.info("Successfully checkout to latest dataset.")

        except subprocess.CalledProcessError as e:
            logging.error(f"ERROR: Failed to clone repository from {pecv_bench_url}.")
            logging.error(f"Stderr: {e.stderr}")
            sys.exit(1)

def install_pecv_bench_dependencies(project_path: str) -> None:
    """
    Installs the pecv-bench project in editable mode to get all dependencies.

    :param str project_path: The path to the project directory
    :raises SystemExit: if the pip install command fails
    """
    if not os.path.exists(project_path):
        logging.error(f"ERROR: The specified project path {project_path} does not exist.")
        logging.error("Clone pecv-bench first by calling 'clone_pecv_bench' function.")
        sys.exit(1)

    logging.info(f"Installing dependencies for pecv-bench from {project_path}...")
    try:
        # Pydantic-core/PyO3 build fails on Python 3.14 without this flag
        # as it thinks 3.14 is too new. We force it to use the stable ABI.
        env = os.environ.copy()
        env["PYO3_USE_ABI3_FORWARD_COMPATIBILITY"] = "1"
        subprocess.run(
            [sys.executable, "-m", "pip", "install", "-e", "."],
            check=True,
            cwd=project_path,
            env=env
        )
        logging.info("Successfully installed pecv-bench dependencies.")
    except subprocess.CalledProcessError as e:
        logging.error(f"ERROR: Failed to install pecv-bench dependencies from {project_path}.")
        logging.error(f"Pip install stderr: {e.stderr}")
        sys.exit(1)

def create_exercise_variants(course, exercise) -> None:
    """
    Imports VariantManager and ExerciseIdentifier from pecv-bench and creates all variants with materialize_variant func.

    Requires that pecv-bench is in sys.path and dependencies are installed.

    This function applies the git patch file to create the variant.

    :param str course: The course identifier
    :param str exercise: The exercise identifier

    :raises Exception: if pecv_bench is not in sys.path
    """
    logging.info(f"Creating ALL variants for {course}/{exercise}...")
    if get_pecv_bench_dir() not in sys.path:
        logging.error("ERROR: PECV-Bench directory not in sys.path.")
        raise ImportError("PECV-Bench directory not in sys.path")

    try:
        from cli.commands.variants import VariantManager
        from cli.utils import ExerciseIdentifier
        logging.info("Successfully imported VariantManager and ExerciseIdentifier from pecv-bench.")
    except ImportError as e:
        logging.error("ERROR: Failed to import VariantManager and ExerciseIdentifier from pecv-bench.")
        logging.error("call 'install_pecv_bench_dependencies' to install dependencies.")
        raise e

    try:
        exercise_id = ExerciseIdentifier(course=course, exercise=exercise)
        manager = VariantManager(exercise_id)

        all_variants = manager.list_variants()

        if not all_variants:
            logging.info("No variants found.")
            return

        logging.info(f"Found {len(all_variants)} variants. Processing...")

        for variant in all_variants:
            try:
                # 'variant.variant_id' gets the ID string like "001"
                manager.materialize_variant(variant.variant_id, force=True)
                logging.info(f"Generated {variant.variant_id}")
            except Exception as e:
                logging.exception(f"Failed to create variant {variant.variant_id}: {e}")
                continue

        logging.info(f"Successfully created {len(all_variants)} variants.")
    except Exception as e:
        logging.exception(f"Critical Error: {e}")