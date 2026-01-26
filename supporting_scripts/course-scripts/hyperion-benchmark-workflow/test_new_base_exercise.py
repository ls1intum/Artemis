import sys
import os
import configparser
import json
import requests
from concurrent.futures import ThreadPoolExecutor, as_completed
from logging_config import logging

from manage_pecv_bench_course import get_pecv_bench_course_id_request, login_as_admin
from manage_programming_exercise import convert_base_exercise_to_zip, import_programming_exercise_request
from utils import COURSE_EXERCISES, PECV_BENCH_REPO_URL, clone_pecv_bench, get_pecv_bench_dir, login_as_admin, SERVER_URL

def test_new_base_exercise():
    """
    Main entry point for adding new exercises.

    It performs the following steps:
    1. Sets up a session and logs in as an admin.
    2. Retrieves the target course ID.
    3. Sets up the pecv-bench environment.
    4. Iterates through the configured courses and exercises and imports them as base exercises.
    5. Import the programming exercises into Artemis.
    """
    logging.info("Starting Add New Exercises Script (Base Exercises Flow)...")

    # 1. Setup Session & Login
    session = requests.Session()
    login_as_admin(session)

    # 2. Get Course ID
    course_id = get_pecv_bench_course_id_request(session)
    logging.info(f"Target Course ID: {course_id}")

    # 3. Setup PECV-Bench Path
    pecv_bench_dir: str = get_pecv_bench_dir()
    #clone_pecv_bench(PECV_BENCH_REPO_URL, pecv_bench_dir)

    # Iterate through courses and exercises to create base zips
    for course_short_name, exercises in COURSE_EXERCISES.items():
        for exercise_name in exercises:
            exercise_path = os.path.join(pecv_bench_dir, "data", course_short_name, exercise_name)
            if os.path.exists(exercise_path) and os.path.isdir(exercise_path):
                logging.info(f"Creating base zip for exercise: {exercise_name} in course {course_short_name}")
                convert_base_exercise_to_zip(exercise_path, course_id)
                import_programming_exercise_request(session, course_id, SERVER_URL, exercise_path)
            else:
                logging.warning(f"Exercise directory not found: {exercise_path}")

    logging.info("Add New Exercises Script completed.")

if __name__ == "__main__":
    test_new_base_exercise()
