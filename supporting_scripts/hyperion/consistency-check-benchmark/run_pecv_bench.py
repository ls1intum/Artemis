import os
import requests
from logging_config import logging

from utils import DATASET_VERSION, login_as_admin
from course import create_course_request, get_course_id_request, get_exercise_ids_request
from exercises import clone_pecv_bench, clone_pecv_bench_dataset, get_pecv_bench_dataset_dir, install_pecv_bench_dependencies, create_pecv_bench_version_variants, convert_variant_to_zip_all, import_exercise_variants, get_pecv_bench_dir
from report import generate_report_files
from code_snapshot import create_code_snapshot
from merge_request import create_results_pull_request
from consistency_check import consistency_check

if __name__ == "__main__":
    logging.info("Running pecv-bench scripts.")

    logging.info("Step 1: Getting PECV-Bench directories from config")
    pecv_bench_dir = get_pecv_bench_dir()
    pecv_bench_dataset_dir = get_pecv_bench_dataset_dir()

    logging.info("Step 2: cloning pecv-bench and pecv-bench-dataset repositories")
    clone_pecv_bench(pecv_bench_dir)
    clone_pecv_bench_dataset(pecv_bench_dataset_dir)

    logging.info("Step 3: installing pecv-bench dependencies")
    install_pecv_bench_dependencies(pecv_bench_dir)

    logging.info("Step 4: creating exercise variants")
    create_pecv_bench_version_variants()

    # logging.info("Step 5: Creating session")
    # session = requests.Session()

    # logging.info("Step 6: Logging in as admin")
    # login_as_admin(session=session)

    # logging.info("Step 7: Creating Hyperion Benchmark Course")
    # create_course_request(session=session)

    # logging.info("Step 8: Retrieving Hyperion Benchmark Course ID")
    # course_id = get_course_id_request(session=session)

    # logging.info("Step 9: Converting variants to zip files")
    # convert_variant_to_zip_all(session=session)

    # logging.info("Step 10: Importing exercise variants")
    # import_exercise_variants(session=session)

    # logging.info("Step 11: Retrieving programming exercise IDs for the course")
    # exercise_ids = get_exercise_ids_request(session=session, course_id=course_id)

    # logging.info("Step 12: Running consistency checks for all programming exercises")
    # approach_id = consistency_check(session=session, exercise_ids=exercise_ids)

    # logging.info("Step 13: Generating benchmark report")
    # generate_report_files(pecv_bench_dir, DATASET_VERSION, approach_id)

    # logging.info("Step 13b: Creating code snapshot ZIP")
    # approach_results_dir = os.path.join(pecv_bench_dir, "results", approach_id)
    # create_code_snapshot(approach_results_dir, approach_id)

    # logging.info("Step 14: Creating pull request with results in pecv-bench")
    # create_results_pull_request(pecv_bench_dir, approach_id)
