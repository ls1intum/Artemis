from concurrent.futures import ThreadPoolExecutor, as_completed
import configparser
import json
import subprocess
import os
import sys
import requests
from logging import config
from typing import Dict, List, Tuple
from logging_config import logging
from utils import login_as_admin, SERVER_URL
from create_pecv_bench_course import create_pecv_bench_course
from manage_programming_exercise import check_exercise_consistency, convert_variant_to_zip, import_programming_exercise, process_variant_consistency_check
from pathlib import Path

# Load configuration
config = configparser.ConfigParser()
config.read(['config.ini'])

PECV_BENCH_PATH: str = config.get('PECVBenchSettings', 'pecv_bench_path', fallback="pecv-bench")
PECV_BENCH_URL: str = config.get('PECVBenchSettings', 'pecv_bench_repo', fallback="https://github.com/ls1intum/PECV-bench.git")
COURSE: str = config.get('PECVBenchSettings', 'course', fallback="ITP2425")
EXERCISES: List[str] = [exercise.strip() for exercise in config.get('PECVBenchSettings', 'exercises', fallback="H01E01-Lectures").split(',')]
MAX_THREADS: int = int(config.get('Settings', 'max_threads', fallback="5"))
REFERENCE: str = config.get('PECVBenchSettings', 'reference', fallback="No Data Available")

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

def process_single_variant_import(session: requests.Session, 
                                    server_url: str,
                                    course_id: int,
                                    exercise_name: str,
                                    variant_id: str,
                                    variant_id_path: str) -> Tuple[str, int]:
    """
    Worker function to zip and import a single variant.
    Returns: (exercise_variant_local_id, exercise_server_id) or (exercise_variant_local_id, None) on failure.
    """
    dict_key = f"{exercise_name}:{variant_id}"

    # Step 1: Convert variant to zip
    zip_created = convert_variant_to_zip(variant_id_path, course_id)
    if not zip_created:
        logging.error(f"Failed to create zip for {dict_key}. Skipping import.")
        return (dict_key, None)

    # Step 2: Import programming exercise
    try:            
        response_data = import_programming_exercise(session = session, 
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
        
        for EXERCISE in EXERCISES:
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
    programming_exercises: Dict[str, int] = {} # {'<NAME>-001': 92, <VARIANT_ID>: <exercise_id>, ...}
    logging.info(f"Preparing to import variants for {len(EXERCISES)} exercises using {MAX_THREADS} threads")
    with ThreadPoolExecutor(max_workers=MAX_THREADS) as executor:
        futures = []
        
        # submit all tasks
        for EXERCISE in EXERCISES:
            variants_folder_path: str = f"{pecv_bench_dir}/data/{COURSE}/{EXERCISE}/variants"
            list_of_variants = sorted(os.listdir(variants_folder_path))

            for variant_id in list_of_variants:
                if not os.path.isdir(os.path.join(variants_folder_path, variant_id)):
                    continue
                variant_id_path = os.path.join(variants_folder_path, variant_id)

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
            
    logging.info(f"Imported {len(programming_exercises)} programming exercises into course ID {course_id}.")
    print(f"\n{programming_exercises}\n")
    
    # Step 7: Run consistency checks for all programming exercises and store results
    model_name = "azure-openai-gpt-5-mini"  # NOTE future implementation
                                            # NOTE implement PyYAML parser to extract from src/main/resources//config/application-local.yml
                                            # NOTE sprint.ai.mode.chat + spring.ai.azure.openai.chat.options.deployment-name
    
    logging.info(f"Starting consistency checks for {len(programming_exercises)} variants using up to {MAX_THREADS} threads")
    course_dir = os.path.join(pecv_bench_dir, "results", "artemis-benchmark", model_name, "cases", COURSE)
    for EXERCISE in EXERCISES:
        os.makedirs(os.path.join(course_dir, EXERCISE), exist_ok=True)
    
    run_id = f"{model_name}-default"

    with ThreadPoolExecutor(max_workers=MAX_THREADS) as executor:
        futures = []
        
        # variant_id: EXERCISE:variant_id
        for exercise_variant_local_id, exercise_server_id in programming_exercises.items():
            futures.append(executor.submit(
                process_variant_consistency_check,
                session,
                SERVER_URL,
                exercise_variant_local_id,
                exercise_server_id,
                course_dir,
                COURSE,
                run_id
            ))
        
        for future in as_completed(futures):
            try:
                result = future.result()
                logging.info(result)
            except Exception as e:
                logging.exception(f"Thread failed with error: {e}")

    logging.info("All consistency checks completed.")

    # Step 8: Generate report and statistics
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

    logging.info("PECV-Bench Hyperion Benchmark Workflow completed.")

if __name__ == "__main__":  
    main()
