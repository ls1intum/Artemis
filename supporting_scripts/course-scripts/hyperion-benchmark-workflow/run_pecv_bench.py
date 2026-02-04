import requests
from logging_config import logging

from utils import DATASET_VERSION, get_pecv_bench_dir, login_as_admin
from course import create_course_request, get_exercise_ids_request
from exercises import clone_pecv_bench, install_pecv_bench_dependencies, create_exercise_variants_all, convert_variant_to_zip_all, import_exercise_variants
from report import generate_response_file
from consistency_check import consistency_check

if __name__ == "__main__":
    logging.info("Creating Hyperion Benchmark Course")

    logging.info("Step 1: Creating session")
    session = requests.Session()

    logging.info("Step 2: Logging in as admin")
    login_as_admin(session=session)

    logging.info("Step 3: Creating Hyperion Benchmark Course")
    response = create_course_request(session=session)

    logging.info("Step 4: Retrieving Hyperion Benchmark Course ID")
    course_id = response.get("id")

    pecv_bench_dir = get_pecv_bench_dir()

    logging.info("Step 5: cloning pecv-bench repository")
    clone_pecv_bench(pecv_bench_dir)

    logging.info("Step 6: installing pecv-bench dependencies")
    install_pecv_bench_dependencies(pecv_bench_dir)

    logging.info("Step 7: creating exercise variants")
    create_exercise_variants_all()

    logging.info("Step 8: converting variants to zip files")
    convert_variant_to_zip_all(session=session)

    logging.info("Step 9: importing exercise variants")
    import_exercise_variants(session=session)

    logging.info("Step 10: Retrieving programming exercise IDs for the course")
    exercise_ids = get_exercise_ids_request(session=session, course_id=course_id)

    logging.info("Step 11: Running consistency checks for all programming exercises")
    approach_id = consistency_check(session=session, exercise_ids=exercise_ids)

    logging.info("Step 12: Generating benchmark report")
    generate_response_file(pecv_bench_dir, DATASET_VERSION, approach_id)