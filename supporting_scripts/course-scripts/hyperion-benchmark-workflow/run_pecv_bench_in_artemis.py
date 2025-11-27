import configparser
import json
import subprocess
import os
import sys
import requests
from logging import config
from typing import Dict, Tuple
from logging_config import logging
from utils import login_as_admin, SERVER_URL, CLIENT_URL
from create_pecv_bench_course import create_pecv_bench_course
from manage_programming_exercise import check_consistency, convert_variant_to_zip, import_programming_exercise
from pathlib import Path

# Load configuration
config = configparser.ConfigParser()
config.read(['config.ini'])

PECV_BENCH_PATH: str = config.get('PECVBenchSettings', 'pecv_bench_path', fallback="pecv-bench")
PECV_BENCH_URL: str = config.get('PECVBenchSettings', 'pecv_bench_repo', fallback="https://github.com/ls1intum/PECV-bench.git")
COURSE: str = config.get('PECVBenchSettings', 'course', fallback="ITP2425")
EXERCISE: str = config.get('PECVBenchSettings', 'exercise', fallback="H01E01-Lectures")

def clone_pecv_bench(pecv_bench_url: str, pecv_bench_dir: str) -> None:
    """Clones a repository if it doesn't exist, or pulls updates if it does."""

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

def create_variant(course, exercise, variant_id):
    """
    Imports VariantManager and ExerciseIdentifier from pecv-bench and creates a specific variant with materialize_variant func.

    This function applies the git patch file to create the variant.
    """
    logging.info(f"Creating variant: {variant_id}...")

    from cli.commands.variants import VariantManager
    from cli.utils import ExerciseIdentifier

    try:
        exercise_id = ExerciseIdentifier(course=course, exercise=exercise)
        manager = VariantManager(exercise_id)
        
        # apply git patch
        manager.materialize_variant(variant_id, force=True)
        logging.info(f"Successfully created variant {variant_id}.")
    except Exception as e:
        logging.exception(f"Failed creating exercise from git patch file: {e}")
        raise e

def create_all_variants(course, exercise):
    """
    Imports VariantManager and ExerciseIdentifier from pecv-bench and creates all variants with materialize_variant func.
    
    This function applies the git patch file to create the variant.
    """
    logging.info(f"Creating ALL variants for {course}/{exercise}...")
    
    from cli.commands.variants import VariantManager
    from cli.utils import ExerciseIdentifier
    logging.info("Successfully imported VariantManager and ExerciseIdentifier from pecv-bench.")

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
                logging.exception(f"Failed to create variant {variant.variant_id}: {e}")
                continue
    
        logging.info(f"Successfully created {len(all_variants)} variants.")
    except Exception as e:
        logging.exception(f"Critical Error: {e}")

def main():
    logging.info("Starting PECV-Bench Hyperion Benchmark Workflow...")
    
    # Step 1: Clone pecv-bench repository
    hyperion_benchmark_workflow_dir = os.path.dirname(os.path.abspath(__file__))
    pecv_bench_dir = os.path.join(hyperion_benchmark_workflow_dir, PECV_BENCH_PATH)
    clone_pecv_bench(PECV_BENCH_URL, pecv_bench_dir)

    # Step 2: Install pecv-bench dependencies
    install_pecv_bench_dependencies(pecv_bench_dir)

    # Step 3: Import necessary modules from pecv-bench and create variants
    if pecv_bench_dir not in sys.path:
        sys.path.insert(0, pecv_bench_dir)
    try:
        from cli.commands.variants import VariantManager
        from cli.utils import ExerciseIdentifier
    
        create_all_variants(COURSE, EXERCISE)

    except ImportError as e:
        logging.error(f"Failed to import dependencies from pecv-bench. Error: {e}")
        sys.exit(1)
    
    # Step 4: Create session and authenticate
    session = requests.Session()
    login_as_admin(session)

    # Step 5: Create PECV Bench Course
    response_data = create_pecv_bench_course(session)
    course_id = response_data["id"]
    
    # Step 6: Store variant_id to exercise_id mapping, create zip files and import programming exercises
    # """
    #PROGRAMMING_EXERCISES = {'001': 92, '002': 93, '003': 94, '004': 95, '005': 96, '006': 97, '007': 98, '008': 99, '009': 100, '010': 101, '011': 102, '012': 103, '013': 104, '014': 105, '015': 106, '016': 107, '017': 108, '018': 109, '019': 110, '020': 111, '021': 112, '022': 113, '023': 114, '024': 115, '025': 116, '026': 117, '027': 118, '028': 119, '029': 120, '030': 121}
    # """
    programming_exercises: Dict[str, int] = {} # {'001': 92, <VARIANT_ID>: <exercise_id>, ...}
    variants_folder_path: str = f"{pecv_bench_dir}/data/{COURSE}/{EXERCISE}/variants"
    list_of_variants = sorted(os.listdir(variants_folder_path))
    for variant_id in list_of_variants:
        if not os.path.isdir(os.path.join(variants_folder_path, variant_id)):
            continue
        variant_id_path = os.path.join(variants_folder_path, variant_id)
        programming_exercises[variant_id] = None
        convert_variant_to_zip(variant_id_path, course_id)
        
        response_data = import_programming_exercise(session = session, 
                                course_id = course_id,
                                server_url = SERVER_URL,
                                variant_folder_path = variant_id_path)
        if response_data is not None and response_data["id"] is not None:
            programming_exercises[variant_id] = response_data["id"]
        else:
            logging.error(f"Failed to import programming exercise for variant {variant_id}. Moving to next variant.")
            continue    
    
    # Step 7: Run consistency checks for all programming exercises and store results
    consistency_check_results = os.path.join(pecv_bench_dir, "results", "artemis-bench", COURSE, EXERCISE)
    os.makedirs(consistency_check_results, exist_ok=True)
    
    for variant_id, exercise_id in programming_exercises.items():
        if exercise_id is None:
            logging.error(f"Skipping consistency check for variant {variant_id} due to missing exercise ID.")
            continue
        logging.info(f"Running consistency check for variant {variant_id} with exercise ID {exercise_id}...")
        consistency_issue, exercise_id = check_consistency(session=session, programming_exercise_id=exercise_id, server_url=SERVER_URL)
        with open(os.path.join(consistency_check_results, f"{variant_id}.json"), "w") as file:
            json.dump(consistency_issue, file, indent=4)

    # Step 8: Generate report and statistics
    
    logging.info("PECV-Bench Hyperion Benchmark Workflow completed.")

if __name__ == "__main__":
    main()
