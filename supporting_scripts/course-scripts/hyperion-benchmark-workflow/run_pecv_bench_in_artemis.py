from concurrent.futures import ThreadPoolExecutor, as_completed
import configparser
import json
import subprocess
import os
import sys
import requests
from typing import Dict, List, Tuple
from logging_config import logging
from manage_pecv_bench_course import create_pecv_bench_course_request, get_exercise_ids_from_pecv_bench_request, get_pecv_bench_course_id_request, login_as_admin, SERVER_URL
from manage_programming_exercise import (
    convert_variant_to_zip,
    import_programming_exercise_request,
    consistency_check_variant_io
)

"""
DISCLAIMER: Execution Context Sensitivity
This script relies on 'config.ini' being present in the current working directory.
It uses module-level global variables loaded from this configuration.
Ensure this script is executed from the directory containing 'config.ini'.
"""

# Load configuration
config = configparser.ConfigParser()
config.read(['config.ini'])

PECV_BENCH_PATH: str = config.get('PECVBenchSettings', 'pecv_bench_folder', fallback="pecv-bench")
PECV_BENCH_REPO_URL: str = config.get('PECVBenchSettings', 'pecv_bench_repo', fallback="https://github.com/ls1intum/PECV-bench.git")

course_exercises_raw = config.get('PECVBenchSettings', 'course_exercises', fallback='{}')
COURSE_EXERCISES: Dict[str, List[str]] = json.loads(course_exercises_raw)

MAX_THREADS: int = int(config.get('Settings', 'max_threads', fallback="5"))
REFERENCE: str = config.get('PECVBenchSettings', 'reference', fallback="No Data Available")

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

def install_pecv_bench_dependencies(project_path: str):
    """
    Installs the pecv-bench project in editable mode to get all dependencies.

    :param str project_path: The path to the project directory
    :raises SystemExit: if the pip install command fails
    """
    logging.info(f"Installing dependencies for pecv-bench from {project_path}...")
    try:
        subprocess.run(
            [sys.executable, "-m", "pip", "install", "-e", "."],
            check=True,
            cwd=project_path
        )
        logging.info("Successfully installed pecv-bench dependencies.")
    except subprocess.CalledProcessError as e:
        logging.error(f"ERROR: Failed to install pecv-bench dependencies from {project_path}.")
        logging.error(f"Pip install stderr: {e.stderr}")
        sys.exit(1)

# Helper function
def create_variant(course, exercise, variant_id):
    """
    Imports VariantManager and ExerciseIdentifier from pecv-bench and creates a specific variant with materialize_variant func.

    This function applies the git patch file to create the variant.

    :param str course: The course identifier
    :param str exercise: The exercise identifier
    :param str variant_id: The variant identifier
    :raises Exception: if pecv_bench is not in sys.path
    """
    logging.info(f"Creating variant: {variant_id}...")
    if get_pecv_bench_dir() not in sys.path:
        raise ImportError("PECV-Bench directory not in sys.path")

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

    :param str course: The course identifier
    :param str exercise: The exercise identifier

    :raises Exception: if pecv_bench is not in sys.path
    """
    logging.info(f"Creating ALL variants for {course}/{exercise}...")
    if get_pecv_bench_dir() not in sys.path:
        raise ImportError("PECV-Bench directory not in sys.path")

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
                manager.materialize_variant(variant.variant_id, force=True)
                logging.info(f"Generated {variant.variant_id}")
            except Exception as e:
                logging.exception(f"Failed to create variant {variant.variant_id}: {e}")
                continue

        logging.info(f"Successfully created {len(all_variants)} variants.")
    except Exception as e:
        logging.exception(f"Critical Error: {e}")

def process_single_variant_import(session: requests.Session,
                                    server_url: str,
                                    course_id: int,
                                    exercise_name: str,
                                    variant_id: str,
                                    variant_id_path: str) -> Tuple[str, int]:
    """
    Worker function to zip and import a single variant.

    :param requests.Session session: The authenticated session
    :param str server_url: The server URL
    :param int course_id: The course ID
    :param str exercise_name: The name of the exercise
    :param str variant_id: The variant ID
    :param str variant_id_path: The path to the variant directory
    :return: A tuple containing the exercise key and the exercise ID (or None on failure)
    :rtype: Tuple[str, int]
    """

    dict_key = f"{exercise_name}:{variant_id}"

    zip_created = convert_variant_to_zip(variant_id_path, course_id)
    if not zip_created:
        logging.error(f"Failed to create zip for {dict_key}. Skipping import.")
        return (dict_key, None)

    try:
        response_data = import_programming_exercise_request(session = session,
                                    course_id = course_id,
                                    server_url = server_url,
                                    variant_folder_path = variant_id_path
                                    )
        exercise_id = response_data.get("id") if response_data else None
        if exercise_id is not None:
            return (dict_key, exercise_id)
        else:
            logging.error(f"Failed to import programming exercise for {dict_key}. Moving to next variant.")
            return (dict_key, None)
    except Exception as e:
        logging.exception(f"Exception during import of {dict_key}: {e}")
        return (dict_key, None)

def summarize_report(report_md_path: str, summary_md_path: str) -> None:
    """
    Reads content from summary.md and inserts it into report.md
    immediately after the '# Variants Analysis Report' header.

    :param str report_md_path: The path to the report markdown file
    :param str summary_md_path: The path to the summary markdown file
    """
    if not os.path.exists(report_md_path):
        logging.error(f"Report file not found at {report_md_path}")
        return

    if not os.path.exists(summary_md_path):
        logging.error(f"Summary file not found at {summary_md_path}")
        return

    try:
        # read the summary text
        with open(summary_md_path, 'r', encoding='utf-8') as f:
            summary_text = f.readlines()

        new_summary_text = []
        inserted_reference = False
        for line in summary_text:
            if not inserted_reference and line.startswith("| artemis-benchmark "):
                new_summary_text.append(line)
                new_summary_text.append(REFERENCE + "\n")
                inserted_reference = True
                continue
            new_summary_text.append(line)

        # read the report lines
        with open(report_md_path, 'r', encoding='utf-8') as f:
            lines = f.readlines()

        new_lines = []
        inserted = False

        header_marker = "# Variants Analysis Report"

        for line in lines:
            new_lines.append(line)
            if not inserted and header_marker in line:
                # add a newline, the summary text, and another newline
                new_lines.append("\n" + "".join(new_summary_text) + "\n")
                inserted = True

        # write back to report.md
        with open(report_md_path, 'w', encoding='utf-8') as f:
            f.writelines(new_lines)

        logging.info(f"Successfully injected summary from {os.path.basename(summary_md_path)} into {os.path.basename(report_md_path)}")

    except Exception as e:
        logging.exception(f"Error while injecting summary into report: {e}")

# MAIN WORKFLOW

def get_pecv_bench_dir() -> str:
    """
    Gets the directory path for pecv-bench.

    :return: The absolute path to the pecv-bench directory
    :rtype: str
    """
    hyperion_benchmark_workflow_dir = os.path.dirname(os.path.abspath(__file__))
    pecv_bench_dir = os.path.join(hyperion_benchmark_workflow_dir, PECV_BENCH_PATH)
    return pecv_bench_dir

def create_session() -> requests.Session:
    """
    Creates and logs in a new session.

    :return: An authenticated requests Session
    :rtype: requests.Session
    """
    session = requests.Session()
    login_as_admin(session)
    return session

def pecv_bench_setup(session: requests.Session) -> Dict[str, int]:
    """
    Sets up the PECV-Bench environment and imports programming exercises.

    This function performs the following steps:
    1. Clones the pecv-bench repository if it does not exist or pulls the latest changes.
    2. Installs necessary pecv-bench dependencies.
    3. Creates all exercise variants based on the configuration.
    4. Creates a new course in Artemis for the benchmark.
    5. Imports each exercise variant as a programming exercise into the course using multiple threads.

    :param requests.Session session: The authenticated session to use for API requests
    :return: A dictionary mapping exercise variant keys to their respective server IDs
    :rtype: Dict[str, int]
    """
    # Clone pecv-bench repository
    pecv_bench_dir = get_pecv_bench_dir()
    clone_pecv_bench(PECV_BENCH_REPO_URL, pecv_bench_dir)

    # Install pecv-bench dependencies
    install_pecv_bench_dependencies(pecv_bench_dir)

    # Import necessary modules from pecv-bench and create variants
    if pecv_bench_dir not in sys.path:
        sys.path.insert(0, pecv_bench_dir)
    try:
        for COURSE, EXERCISES in COURSE_EXERCISES.items():
            for EXERCISE in EXERCISES:
                create_all_variants(COURSE, EXERCISE)

    except ImportError as e:
        logging.error(f"Failed to import dependencies from pecv-bench. Error: {e}")
        sys.exit(1)

    # Create PECV Bench Course
    response_data = create_pecv_bench_course_request(session)
    course_id = response_data.get("id")

    # Store variant_id to exercise_id mapping, create zip files and import programming exercises
    programming_exercises: Dict[str, int] = {} # {'<NAME>:001': 92, <VARIANT_ID>: <exercise_id>, ...}
    logging.info(f"Preparing to import variants for {sum(len(ex) for ex in COURSE_EXERCISES.values())} exercises across {len(COURSE_EXERCISES)} courses using {MAX_THREADS} threads")
    with ThreadPoolExecutor(max_workers=MAX_THREADS) as executor:
        futures = []

        # submit all tasks
        for COURSE, EXERCISES in COURSE_EXERCISES.items():
            for EXERCISE in EXERCISES:
                variants_folder_path: str = f"{pecv_bench_dir}/data/{COURSE}/{EXERCISE}/variants"

                if not os.path.exists(variants_folder_path):
                    logging.warning(f"Variants folder not found: {variants_folder_path}")
                    continue

                list_of_variants = sorted(os.listdir(variants_folder_path))

                for variant_id in list_of_variants:
                    if not os.path.isdir(os.path.join(variants_folder_path, variant_id)):
                        continue
                    variant_id_path = os.path.join(variants_folder_path, variant_id)
                    #exercise_name = EXERCISE.split('-')[0].strip()
                    futures.append(executor.submit(
                        process_single_variant_import,
                        session,
                        SERVER_URL,
                        course_id,
                        EXERCISE,
                        variant_id,
                        variant_id_path
                    ))

        # collect results as they finish and thread-safe dictionary update
        for future in as_completed(futures):
            try:
                key, exercise_server_id = future.result()
                if exercise_server_id is not None:
                    programming_exercises[key] = exercise_server_id
                    logging.info(f"Imported variant {key} with exercise ID {exercise_server_id}.")
                else:
                    logging.error(f"Failed to import variant {key}.")
            except Exception as e:
                logging.exception(f"Thread failed with error: {e}")
                return {}

    logging.info(f"Imported {len(programming_exercises)} programming exercises into course ID {course_id}.")
    return programming_exercises

def run_consistency_checks(session: requests.Session, pecv_bench_dir: str, programming_exercises: Dict[str, int]) -> None:
    """
    Run consistency checks for all programming exercises and store results.

    :param requests.Session session: The authenticated session to use for API requests
    :param str pecv_bench_dir: The root directory of the pecv-bench repository
    :param Dict[str, int] programming_exercises: Dictionary mapping exercise variant keys to their server IDs
    """
    model_name = "azure-openai-gpt-5-mini"  # NOTE future implementation
                                            # NOTE implement PyYAML parser to extract from src/main/resources//config/application-local.yml
                                            # NOTE sprint.ai.mode.chat + spring.ai.azure.openai.chat.options.deployment-name

    logging.info(f"Starting consistency checks for {len(programming_exercises)} variants using up to {MAX_THREADS} threads")

    for course, exercises in COURSE_EXERCISES.items():
        results_dir = os.path.join(pecv_bench_dir, "results", "artemis-benchmark", model_name, "cases", course)
        for exercise in exercises:
            os.makedirs(os.path.join(results_dir, exercise), exist_ok=True)

    run_id = f"{model_name}-default"

    with ThreadPoolExecutor(max_workers=MAX_THREADS) as executor:
        futures = []

        # variant_id: EXERCISE:variant_id
        for exercise_variant_local_id, exercise_server_id in programming_exercises.items():
            # parse exercise name from variant key (format: "EXERCISE:variant_id")
            exercise_name = exercise_variant_local_id.split(':')[0]

            # Find course for this exercise
            course = next(
                (c for c, exs in COURSE_EXERCISES.items() if exercise_name in exs),
                None
            )

            if course is None:
                logging.warning(f"Could not find course for exercise {exercise_name}, skipping")
                continue

            results_dir = os.path.join(pecv_bench_dir, "results", "artemis-benchmark", model_name, "cases", course)

            futures.append(executor.submit(
                consistency_check_variant_io,
                session,
                SERVER_URL,
                exercise_variant_local_id,
                exercise_server_id,
                results_dir,
                course,
                run_id
            ))

        for future in as_completed(futures):
            try:
                result = future.result()
                logging.info(result)
            except Exception as e:
                logging.exception(f"Thread failed with error: {e}")

    logging.info("All consistency checks completed.")

def generate_response_file(pecv_bench_dir: str) -> None:
    """
    Generate report and statistics for the benchmark results.

    :param str pecv_bench_dir: The root directory of the pecv-bench repository
    :raises SystemExit: If any of the pecv-bench CLI commands fail
    """

    logging.info("Generating analysis and reports...")

    commands = [
        ["pecv-bench", "variants-analysis", "--results-dir", "results/artemis-benchmark", "--clear"],
        ["pecv-bench", "variants-analysis", "--results-dir", "results/artemis-benchmark", "--plot"],
        ["pecv-bench", "report", "--benchmark", "artemis-benchmark"]
    ]

    for cmd in commands:
        try:
            logging.info(f"Running command: {' '.join(cmd)}")

            subprocess.run(
                cmd,
                cwd=pecv_bench_dir,
                check=True,
                capture_output=True
            )
        except subprocess.CalledProcessError as e:
            logging.error(f"Command failed: {' '.join(cmd)}")
            logging.error(f"Error: {e}")

            sys.exit(1)

    report_md_path = os.path.join(pecv_bench_dir, "results", "artemis-benchmark", "report.md")
    summary_md_path = os.path.join(pecv_bench_dir, "results", "artemis-benchmark", "summary.md")
    summarize_report(report_md_path, summary_md_path)

def main():
    logging.info("Starting PECV-Bench Hyperion Benchmark Workflow...")

    # Step 1: Create session and login as admin
    session = create_session()

    # Step 2: get pecv-bench directory
    # pecv_bench_dir = get_pecv_bench_dir()

    # Step 3: Setup PECV Bench and import programming exercises
    programming_exercises = pecv_bench_setup(session)

    # Step 4: Get course ID
    pecv_bench_course_id = get_pecv_bench_course_id_request(session)

    # Step 5: Verify imported programming exercises
    programming_exercises = get_exercise_ids_from_pecv_bench_request(session, pecv_bench_course_id)

    # Step 6: Run consistency checks for all programming exercises and store results
    run_consistency_checks(session, pecv_bench_dir, programming_exercises)

    # Step 7: Generate report and statistics
    generate_response_file(pecv_bench_dir)

    logging.info("PECV-Bench Hyperion Benchmark Workflow completed.")

if __name__ == "__main__":
    main()
