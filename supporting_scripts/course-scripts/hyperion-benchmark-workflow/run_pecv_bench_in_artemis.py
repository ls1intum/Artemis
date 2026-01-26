from concurrent.futures import ThreadPoolExecutor, as_completed
import configparser
import json
import subprocess
import os
import sys
import requests
from typing import Dict, List, Tuple
from logging_config import logging
from manage_pecv_bench_course import create_pecv_bench_course_request, get_exercise_ids_from_pecv_bench_request, get_pecv_bench_course_id_request, login_as_admin, SERVER_URL, setup_pecv_bench_course
from manage_programming_exercise import (
    convert_variant_to_zip,
    import_programming_exercise_request,
    consistency_check_variant_io
)
from utils import get_pecv_bench_dir, clone_pecv_bench, install_pecv_bench_dependencies

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

if __name__ == "__main__":
    logging.info("Starting PECV-Bench Hyperion Benchmark Workflow...")

    session = requests.Session()

    setup_pecv_bench_course(session=session)

    pecv_bench_dir = get_pecv_bench_dir()
    # Step 4: Get course ID
    pecv_bench_course_id = get_pecv_bench_course_id_request(session)

    # Step 5: Verify imported programming exercises
    programming_exercises = get_exercise_ids_from_pecv_bench_request(session, pecv_bench_course_id)

    # Step 6: Run consistency checks for all programming exercises and store results
    run_consistency_checks(session, pecv_bench_dir, programming_exercises)

    # Step 7: Generate report and statistics
    generate_response_file(pecv_bench_dir)

    logging.info("PECV-Bench Hyperion Benchmark Workflow completed.")
