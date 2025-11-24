import json
from logging import config
import subprocess
import os
import sys
from typing import Dict, Tuple
import requests
from logging_config import logging
from utils import login_as_admin, SERVER_URL, CLIENT_URL
from create_pecv_bench_course import create_pecv_bench_course
from manage_programming_exercise import check_consistency, create_programming_exercise, convert_variant_to_zip, import_programming_exercise
from pathlib import Path

PECV_BENCH_DIR: str = "pecv-bench"
PECV_BENCH_URL: str = "https://github.com/ls1intum/PECV-bench.git"

def clone_pecv_bench(pecv_bench_url: str, pecv_bench_dir: str) -> None:
    """Clones a repository if it doesn't exist, or pulls updates if it does."""

    if os.path.exists(pecv_bench_dir):
        logging.info(f"Directory {PECV_BENCH_DIR} already exists. Pulling latest changes.")
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
        except subprocess.CalledProcessError as e:
            logging.error(f"ERROR: Failed to clone repository from {pecv_bench_url}.")
            logging.error(f"Stderr: {e.stderr}")
            sys.exit(1)

def install_pecv_bench_dependencies(project_path: str):
    """Installs the pecv-bench project in editable mode to get all dependencies."""
    logging.info(f"Installing dependencies for pecv-bench from {project_path}...")
    try:
        subprocess.run(
            [sys.executable, "-m", "pip", "install", "-e", "."],
            check=True,
            capture_output=True,
            text=True,
            cwd=project_path  # Run the command in the pecv-bench directory
        )
        logging.info("Successfully installed pecv-bench dependencies.")
    except subprocess.CalledProcessError as e:
        logging.error(f"ERROR: Failed to install pecv-bench dependencies from {project_path}.")
        logging.error(f"Pip install stderr: {e.stderr}")
        sys.exit(1)

def create_specific_variant(course, exercise, variant_id):
    from cli.commands.variants import VariantManager
    from cli.utils import ExerciseIdentifier
    logging.info(f"Creating variant: {variant_id}...")
    try:
        exercise_id = ExerciseIdentifier(course=course, exercise=exercise)
        manager = VariantManager(exercise_id)
        
        # apply git patch
        manager.materialize_variant(variant_id, force=True)
    except Exception as e:
        logging.info(f"Failed creating exercise from git patch file: {e}")
        raise e
    logging.info(f"Successfully created specific variant.")


def create_all_variants(course, exercise):
    from cli.commands.variants import VariantManager
    from cli.utils import ExerciseIdentifier
    logging.info(f"Creating ALL variants for {course}/{exercise}...")
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
                path = manager.materialize_variant(variant.variant_id, force=True)
                logging.info(f"Generated {variant.variant_id}")
            except Exception as e:
                logging.info(f"Failed {variant.variant_id}: {e}")
    
    except Exception as e:
        logging.info(f"Critical Error: {e}")
    logging.info(f"Successfully created all variants.")

def main():
    hyperion_benchmark_workflow_dir = os.path.dirname(os.path.abspath(__file__))
    pecv_bench_dir = os.path.join(hyperion_benchmark_workflow_dir, PECV_BENCH_DIR)

    clone_pecv_bench(PECV_BENCH_URL, pecv_bench_dir)
    install_pecv_bench_dependencies(pecv_bench_dir)
    if pecv_bench_dir not in sys.path:
        sys.path.insert(0, pecv_bench_dir)
    try:
        from cli.commands.variants import VariantManager
        from cli.utils import ExerciseIdentifier

        logging.info("Successfully imported VariantManager and ExerciseIdentifier from pecv-bench.")
        
        COURSE = "ITP2425"
        EXERCISE = "H01E01-Lectures"
        TARGET_VARIANT_ID = "001"
        #create_specific_variant(COURSE, EXERCISE, TARGET_VARIANT_ID)
        create_all_variants(COURSE, EXERCISE)

    except ImportError as e:
        logging.error(f"Failed to import variantManager: {e}")
        sys.exit(1)
    
    VARIANTS_FOLDER_PATH: str = f"{pecv_bench_dir}/data/{COURSE}/{EXERCISE}/variants"
    list_of_variants = sorted(os.listdir(VARIANTS_FOLDER_PATH))
    
    session = requests.Session()
    login_as_admin(session)

    response_data = create_pecv_bench_course(session)
    course_id = response_data["id"]
    logging.info(f"Created PECV Bench Course with ID: {course_id}")
    
    PROGRAMMING_EXERCISES: Dict[str, int] = {} # {'001': 92, <VARIANT_ID>: <exercise_id>, ...}
    # """
    #PROGRAMMING_EXERCISES = {'001': 92, '002': 93, '003': 94, '004': 95, '005': 96, '006': 97, '007': 98, '008': 99, '009': 100, '010': 101, '011': 102, '012': 103, '013': 104, '014': 105, '015': 106, '016': 107, '017': 108, '018': 109, '019': 110, '020': 111, '021': 112, '022': 113, '023': 114, '024': 115, '025': 116, '026': 117, '027': 118, '028': 119, '029': 120, '030': 121}
    # """
    for VARIANT_ID in list_of_variants:
        if not os.path.isdir(os.path.join(VARIANTS_FOLDER_PATH, VARIANT_ID)):
            continue
        VARIANT_ID_PATH = os.path.join(VARIANTS_FOLDER_PATH, VARIANT_ID)
        PROGRAMMING_EXERCISES[VARIANT_ID] = None
        convert_variant_to_zip(VARIANT_ID_PATH, course_id)
    
        response_data = import_programming_exercise(session = session, 
                                course_id = course_id,
                                server_url = SERVER_URL,
                                variant_folder_path = VARIANT_ID_PATH)
        if response_data is not None and response_data["id"] is not None:
            PROGRAMMING_EXERCISES[VARIANT_ID] = response_data["id"]
        else:
            logging.error(f"Failed to import programming exercise for variant {VARIANT_ID}. Moving to next variant.")
            continue    

    consistency_check_results = os.path.join(pecv_bench_dir, "results", "artemis-bench", COURSE, EXERCISE)
    os.makedirs(consistency_check_results, exist_ok=True)
    
    for variant_id, exercise_id in PROGRAMMING_EXERCISES.items():
        if exercise_id is None:
            logging.error(f"Skipping consistency check for variant {variant_id} due to missing exercise ID.")
            continue
        logging.info(f"Running consistency check for variant {variant_id} with exercise ID {exercise_id}...")
        consistency_issue, exercise_id = check_consistency(session=session, programming_exercise_id=exercise_id, server_url=SERVER_URL)
        with open(os.path.join(consistency_check_results, f"{variant_id}.json"), "w") as file:
            json.dump(consistency_issue, file, indent=4)

    # TODO after that somehow call already existing code from pecv bench, which iterates over results json file for each specific variand, compares it to gold standart
    # NOTE which is variantID.json in the same folder as variandID.patch file
    # TODO after that it automatically create a variants_report.json, and generates a statistics and plots
    

if __name__ == "__main__":
    logging.info("Starting PECV-Bench Hyperion Benchmark Workflow...")
    main()
    logging.info("PECV-Bench Hyperion Benchmark Workflow completed.")
