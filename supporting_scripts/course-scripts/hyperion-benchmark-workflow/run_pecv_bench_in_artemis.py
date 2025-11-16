from logging import config
import subprocess
import os
import sys
import uuid
import requests
from logging_config import logging
from utils import login_as_admin, SERVER_URL
from create_pecv_bench_course import create_pecv_bench_course
from manage_programming_exercise import create_programming_exercise

PECV_BENCH_URL: str = "https://github.com/ls1intum/PECV-bench.git"
PECV_BENCH_DIR: str = "pecv-bench"

def clone_pecv_bench(PECV_BENCH_URL: str, PECV_BENCH_DIR: str) -> None:
    """Clones a repository if it doesn't exist, or pulls updates if it does."""

    if os.path.exists(PECV_BENCH_DIR):
        logging.info(f"Directory {PECV_BENCH_DIR} already exists. Pulling latest changes.")
        try:
            subprocess.run(
                ["git", "pull"],
                cwd=PECV_BENCH_DIR,
                check=True,
            )
            logging.info("Successfully pulled latest changes.")
        except subprocess.CalledProcessError as e:
            logging.error(f"ERROR: Failed to pull updates for {PECV_BENCH_DIR}.")
            logging.error(f"Stderr: {e.stderr}")
            sys.exit(1)
    else:
        logging.info(f"Cloning repository from {PECV_BENCH_URL} into {PECV_BENCH_DIR}.")
        try:
            subprocess.run(
                ["git", "clone", PECV_BENCH_URL, PECV_BENCH_DIR],
                check=True,
            )
            logging.info("Successfully cloned the repository.")
        except subprocess.CalledProcessError as e:
            logging.error(f"ERROR: Failed to clone repository from {PECV_BENCH_URL}.")
            logging.error(f"Stderr: {e.stderr}")
            sys.exit(1)

if __name__ == "__main__":

    logging.info("Starting PECV Benchmark script...")

    script_dir = os.path.dirname(os.path.abspath(__file__))
    full_clone_path = os.path.join(script_dir, PECV_BENCH_DIR)

    clone_pecv_bench(PECV_BENCH_URL, full_clone_path)


    session = requests.Session()
    login_as_admin(session)

    #response_data = create_pecv_bench_course(session)
    #course_id = response_data["id"]
    course_id = 5
    random_slug = str(uuid.uuid4())[:8]
    create_programming_exercise(session, course_id, SERVER_URL, 1, f"Variant 1-{random_slug}")