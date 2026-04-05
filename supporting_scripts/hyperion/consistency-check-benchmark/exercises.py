import re
import shutil
import subprocess
import sys
import os
import json
from typing import Any, Dict, List
import zipfile
import requests
import urllib3
from concurrent.futures import ThreadPoolExecutor, as_completed
from logging_config import logging

from utils import MAX_THREADS, PECV_BENCH_DIR, PECV_BENCH_URL, PECV_BENCH_BRANCH, PECV_BENCH_DATASET_DIR, PECV_BENCH_DATASET_URL, DATASET_VERSION, COURSE_EXERCISES, SERVER_URL, login_as_admin
from course import get_course_id_request

def get_pecv_bench_dir() -> str:
    """
    Gets the directory path for pecv-bench.

    :return: The absolute path to the pecv-bench directory
    :rtype: str
    """
    logging.info("Getting pecv-bench directory path...")
    hyperion_benchmark_workflow_dir = os.path.dirname(os.path.abspath(__file__))
    pecv_bench_dir = os.path.join(hyperion_benchmark_workflow_dir, PECV_BENCH_DIR)
    logging.info(f"PECV-bench directory path: {pecv_bench_dir}")
    return pecv_bench_dir

def clone_pecv_bench(pecv_bench_dir: str) -> None:
    """
    Clones a repository if it doesn't exist, or pulls updates if it does.

    :param str pecv_bench_dir: The directory where the repository should be cloned
    :raises SystemExit: if the git command fails
    """

    if os.path.exists(pecv_bench_dir):
        logging.info(f"Directory {pecv_bench_dir} already exists. Pulling latest changes.")
        try:
            subprocess.run(
                ["git","reset", "--hard", "HEAD"],
                cwd=pecv_bench_dir,
                check=True)
            subprocess.run(
                ["git", "clean", "-fd"],
                cwd=pecv_bench_dir,
                check=True)
            checkout_pecv_bench_benchmark_branch(cwd=pecv_bench_dir)
            subprocess.run(
                ["git", "pull"],
                cwd=pecv_bench_dir,
                check=True,
            )
            logging.info("Successfully pulled latest changes.")

        except subprocess.CalledProcessError as e:
            logging.error(f"Step 2 failed: Failed to pull updates for {pecv_bench_dir}. Error: {e}")
            logging.error("Check pecv_bench_dir in config.ini, then execute Step 1 and Step 2 in exercises.py")
            sys.exit(1)
    else:
        logging.info(f"Cloning repository from {PECV_BENCH_URL} into {pecv_bench_dir}.")
        try:
            subprocess.run(
                ["git", "clone", PECV_BENCH_URL, pecv_bench_dir],
                check=True,
            )
            logging.info("Successfully cloned the repository.")

            checkout_pecv_bench_benchmark_branch(pecv_bench_dir)

        except subprocess.CalledProcessError as e:
            logging.error(f"Step 2 failed: Failed to clone repository from {PECV_BENCH_URL}. Error: {e}")
            logging.error("Check pecv_bench_dir in config.ini, then execute Step 1 and Step 2 in exercises.py")
            sys.exit(1)

def checkout_pecv_bench_benchmark_branch(cwd: str) -> None:
    """
    Checks out the pecv-bench branch configured via ``pecv_bench_branch`` in
    config.ini (default: ``main``).

    :param str cwd: The working directory for the git checkout command.
    :raises SystemExit: if the git checkout command fails.
    """
    try:
        subprocess.run(
                ["git", "checkout", PECV_BENCH_BRANCH],
                cwd=cwd,
                check=True,
            )
        logging.info(f"Successfully checked out branch '{PECV_BENCH_BRANCH}'.")
    except subprocess.CalledProcessError as e:
        logging.error(
            f"Step 2 failed: Failed to checkout branch '{PECV_BENCH_BRANCH}' in {cwd}. "
            f"Verify that the branch exists and that 'pecv_bench_branch' in config.ini is correct. Error: {e}. "
            f"Execute Step 1 and Step 2 in exercises.py"
        )
        sys.exit(1)

def get_pecv_bench_dataset_dir() -> str:
    """
    Gets the directory path for pecv-bench-dataset.

    :return: The absolute path to the pecv-bench-dataset directory
    :rtype: str
    """
    logging.info("Getting pecv-bench-dataset directory path...")
    hyperion_benchmark_workflow_dir = os.path.dirname(os.path.abspath(__file__))
    pecv_bench_dataset_dir = os.path.join(hyperion_benchmark_workflow_dir, PECV_BENCH_DATASET_DIR)
    logging.info(f"PECV-bench-dataset directory path: {pecv_bench_dataset_dir}")
    return pecv_bench_dataset_dir

def clone_pecv_bench_dataset(pecv_bench_dataset_dir: str) -> None:
    """
    Clones the PECV-bench-dataset repository if it doesn't exist, or pulls updates if it does.

    After cloning/pulling, merges the dataset folders into the corresponding course folders
    inside pecv_bench/data/{DATASET_VERSION}/QCSL25/.

    :param str pecv_bench_dataset_dir: The directory where the dataset repository should be cloned
    :raises SystemExit: if the git command fails
    """
    if os.path.exists(pecv_bench_dataset_dir):
        logging.info(f"Directory {pecv_bench_dataset_dir} already exists. Pulling latest changes.")
        try:
            subprocess.run(
                ["git", "reset", "--hard", "HEAD"],
                cwd=pecv_bench_dataset_dir,
                check=True,
            )
            subprocess.run(
                ["git", "clean", "-fd"],
                cwd=pecv_bench_dataset_dir,
                check=True,
            )
            subprocess.run(
                ["git", "pull"],
                cwd=pecv_bench_dataset_dir,
                check=True,
            )
            logging.info("Successfully pulled latest changes for pecv-bench-dataset.")
        except subprocess.CalledProcessError as e:
            logging.error(f"Step 2 failed: Failed to pull updates for {pecv_bench_dataset_dir}. Error: {e}")
            logging.error("Check pecv_bench_dataset_dir in config.ini, then execute Step 1 and Step 2 in exercises.py")
            sys.exit(1)
    else:
        logging.info(f"Cloning repository from {PECV_BENCH_DATASET_URL} into {pecv_bench_dataset_dir}.")
        try:
            subprocess.run(
                ["git", "clone", PECV_BENCH_DATASET_URL, pecv_bench_dataset_dir],
                check=True,
            )
            logging.info("Successfully cloned the pecv-bench-dataset repository.")
        except subprocess.CalledProcessError as e:
            logging.error(f"Step 2 failed: Failed to clone repository from {PECV_BENCH_DATASET_URL}. Error: {e}")
            logging.error("Check pecv_bench_dataset_dir in config.ini, then execute Step 1 and Step 2 in exercises.py")
            sys.exit(1)

    _merge_pecv_bench_dataset(pecv_bench_dataset_dir)

def _merge_pecv_bench_dataset(pecv_bench_dataset_dir: str) -> None:
    """
    Merges the ``data/`` directory from the cloned PECV-bench-dataset repository
    into the ``data/`` directory of pecv-bench, preserving the identical path structure.

    The dataset repo is expected to mirror the pecv-bench layout exactly:

    .. code-block:: text

        pecv-bench-dataset/
        └── data/
            └── V2/
                └── QCSL25/
                    ├── QC01.../
                    │   └── <exercise-specific subfolders/files>
                    ├── QC02.../
                    └── QC03.../

    Every file and folder found under ``pecv-bench-dataset/data/`` is copied into
    the matching location under ``pecv-bench/data/``, overwriting existing files.
    This works for any version, course, or exercise added in the future without
    any script changes.

    :param str pecv_bench_dataset_dir: The path to the cloned pecv-bench-dataset directory
    """
    pecv_bench_dir = get_pecv_bench_dir()
    src_data = os.path.join(pecv_bench_dataset_dir, "data")
    dst_data = os.path.join(pecv_bench_dir, "data")

    if not os.path.exists(src_data):
        logging.error(f"Step 2 failed: No 'data' directory found in pecv-bench-dataset: {src_data}")
        logging.error("Execute Step 1 and Step 2 in exercises.py")
        sys.exit(1)

    if not os.path.exists(dst_data):
        logging.error(f"Step 2 failed: No 'data' directory found in pecv-bench: {dst_data}")
        logging.error("Execute Step 1 and Step 2 in exercises.py to clone pecv-bench first")
        sys.exit(1)

    logging.info(f"Merging {src_data} -> {dst_data}")
    shutil.copytree(src_data, dst_data, dirs_exist_ok=True)
    logging.info("Successfully merged pecv-bench-dataset into pecv-bench.")

def install_pecv_bench_dependencies(pecv_bench_dir: str) -> None:
    """
    Installs the pecv-bench project in editable mode to get all dependencies.

    :param str pecv_bench_dir: The path to the project directory
    :raises SystemExit: if the pip install command fails
    """
    if not os.path.exists(pecv_bench_dir):
        logging.error(f"Step 3 failed: The specified project path {pecv_bench_dir} does not exist.")
        logging.error("Execute Step 1 and Step 2 in exercises.py first, then execute Step 3")
        sys.exit(1)

    logging.info(f"Installing dependencies in {pecv_bench_dir}...")
    try:
        # Pydantic-core/PyO3 build fails on Python 3.14 without this flag
        # as it thinks 3.14 is too new. We force it to use the stable ABI.
        env = os.environ.copy()
        env["PYO3_USE_ABI3_FORWARD_COMPATIBILITY"] = "1"
        subprocess.run(
            [sys.executable, "-m", "pip", "install", "-e", "."],
            check=True,
            cwd=pecv_bench_dir,
            env=env
        )
        logging.info("Successfully installed pecv-bench dependencies.")
    except subprocess.CalledProcessError as e:
        logging.error(f"Step 3 failed: Failed to install pecv-bench dependencies from {pecv_bench_dir}.")
        logging.error(f"Pip install stderr: {e.stderr}")
        logging.error("Execute Step 3 in exercises.py")
        sys.exit(1)

def create_pecv_bench_version_variants() -> None:
    """
    Creates all exercise variants for the VERSION, COURSES and EXERCISES defined in config.ini/COURSE_EXERCISES.

    This function iterates through the COURSE_EXERCISES dictionary and calls create_exercise_variants
    for each course and exercise combination.
    """
    pecv_bench_dir = get_pecv_bench_dir()
    if not check_pecv_bench_setup(pecv_bench_dir):
        return

    variants_to_create = COURSE_EXERCISES.get(DATASET_VERSION, {})
    total = sum(len(exs) for exs in variants_to_create.values())
    processed = 0
    for course, exercises in variants_to_create.items():
        for exercise in exercises:
            logging.info(f"Creating variants for {DATASET_VERSION}/{course}/{exercise}...")
            create_exercise_variants(DATASET_VERSION, course, exercise, pecv_bench_dir)
            processed += 1
    logging.info(f"Variant creation complete: {processed}/{total} exercise(s) processed for {DATASET_VERSION}.")

def check_pecv_bench_setup(pecv_bench_dir: str) -> bool:
    """
    Checks if pecv-bench directory exists and if dependencies are likely installed.

    :param str pecv_bench_dir: The path to the pecv-bench directory.
    :return: ``True`` if setup is valid, ``False`` otherwise.
    :rtype: bool
    """
    if not os.path.exists(pecv_bench_dir):
        logging.error(f"Step 4 failed: PECV-bench directory not found at {pecv_bench_dir}.")
        logging.error("Execute Step 1 and Step 2 in exercises.py")
        return False

    # Simple check if we can import the module (implies installation)
    if pecv_bench_dir not in sys.path:
        sys.path.insert(0, pecv_bench_dir)

    try:
        import cli
        return True
    except ImportError:
        logging.error("Step 4 failed: Could not import 'cli' from pecv-bench. Dependencies might not be installed.")
        logging.error("Execute Step 3 in exercises.py")
        return False

def create_exercise_variants(version: str, course: str, exercise: str, pecv_bench_dir: str) -> None:
    """
    Imports VariantManager and ExerciseIdentifier from pecv-bench and creates all variants with materialize_variant func.

    Requires that pecv-bench is in sys.path and dependencies are installed.

    This function applies the git patch file to create the variant.

    :param str version: The dataset version.
    :param str course: The course identifier.
    :param str exercise: The exercise identifier.
    :param str pecv_bench_dir: The path to the pecv-bench directory.
    :raises Exception: if the pecv-bench CLI imports fail.
    """

    if pecv_bench_dir not in sys.path:
        sys.path.insert(0, pecv_bench_dir)
        logging.info(f"Added PECV-Bench directory to sys.path: {pecv_bench_dir}")

    logging.info(f"Creating ALL variants for {version}/{course}/{exercise}...")
    try:
        from cli.commands.variants import VariantManager
        from cli.utils import ExerciseIdentifier
        logging.info("Successfully imported VariantManager and ExerciseIdentifier from pecv-bench.")
    except ImportError as e:
        logging.error("Step 4 failed: Failed to import VariantManager and ExerciseIdentifier from pecv-bench.")
        logging.error("Execute Step 3 in exercises.py to install dependencies, then execute Step 4")
        raise e

    try:
        exercise_id = ExerciseIdentifier(course=course, exercise=exercise, version=version)
        manager = VariantManager(exercise_id)

        all_variants = manager.list_variants()

        if not all_variants:
            logging.info("No variants found.")
            return

        logging.info(f"Found {len(all_variants)} variants. Processing...")

        succeeded = 0
        for variant in all_variants:
            try:
                # 'variant.variant_id' gets the ID string like "001"
                manager.materialize_variant(variant.variant_id, force=True)
                logging.info(f"Generated {variant.variant_id}")
                succeeded += 1
            except Exception as e:
                logging.exception(f"Step 4 failed: Failed to create variant {variant.variant_id}: {e}")
                continue

        logging.info(f"Successfully created {succeeded}/{len(all_variants)} variants.")
    except Exception as e:
        logging.exception(f"Step 4 failed: Critical error creating variants for {version}/{course}/{exercise}: {e}")
        logging.error("Execute Step 4 in exercises.py")

def sanitize_exercise_name(exercise_name: str, short_name_index: int) -> str:
    """
    Sanitizes the exercise name to create a valid short name.

    Example: "H01E01 - Lectures" -> "H01E01Lectures1"

    :param str exercise_name: The original name of the exercise.
    :param int short_name_index: The index to append to the short name to ensure uniqueness.
    :return: The sanitized short name.
    :rtype: str
    """
    valid_short_name = re.sub(r'[^a-zA-Z0-9]', '', exercise_name)
    if not valid_short_name or not valid_short_name[0].isalpha():
        valid_short_name = f"A{valid_short_name}"
    return f"{valid_short_name}{short_name_index}"

def read_problem_statement(p_s_file_path: str) -> str:
    """
    Reads a markdown file and returns its content as a single string.

    :param str p_s_file_path: The path to the markdown file.
    :return: The content of the file as a string.
    :rtype: str
    """
    with open(p_s_file_path, 'r', encoding='utf-8') as file:
        content = file.read()

    return content

def convert_variant_to_zip(pecv_bench_dir: str, version: str, course: str, exercise: str, variant_id: str, course_id: int) -> bool:
    """
    Converts the programming exercise located at ``variant_path`` into a ZIP file.

    The function renames ``template`` to ``exercise`` and overwrites exercise ID, course ID, title, and shortName in the configuration file.
    It stores the final ZIP file in the same directory as the variant with the following structure:

    .. code-block:: text

        variants:
        |-- 001:
            |-- solution/
            |-- template/
            |-- tests/
            |-- Exercise-Details.json
            |-- 001-FullExercise.zip
                |-- 001-solution.zip
                |-- 001-exercise.zip
                |-- 001-tests.zip
                |-- exercise-details.json

    :param str pecv_bench_dir: The root directory of the pecv-bench repository.
    :param str version: The dataset version identifier (e.g., ``V1``).
    :param str course: The course identifier (e.g., ``QCSL25``).
    :param str exercise: The exercise identifier (e.g., ``QC01-Decision_Diagrams``).
    :param str variant_id: The variant identifier (e.g., ``001``).
    :param int course_id: The ID of the course to which the exercise belongs.
    :return: ``True`` if the ZIP file creation was successful, ``False`` otherwise.
    :rtype: bool
    """

    repo_types: List[str] = ["solution", "template", "tests"]
    config_file: str = "exercise-details.json"
    exercise_zip_filename = f"{variant_id}-FullExercise.zip"

    variant_path = os.path.join(pecv_bench_dir, "data", version, course, exercise, "variants", variant_id)
    exercise_zip_path = os.path.join(variant_path, exercise_zip_filename) #...001/001-FullExercise.zip

    logging.info(f"Final zip file: {exercise_zip_filename} will be created at {exercise_zip_path}")

    # Create intermediate zip files for solution, template and tests
    zip_files = []
    missing_repos: List[str] = []
    try:
        for repo_type in repo_types:
            repo_name = f"{variant_id}-{repo_type}"  #001-solution
            base_name = os.path.join(variant_path, repo_name)

            repo_path = os.path.join(variant_path, repo_type) #001/solution
            if not os.path.exists(repo_path):
                missing_repos.append(repo_type)
                logging.error(f"Step 9 failed: Required folder '{repo_type}' does not exist in {variant_path}.")
                continue
            # Create a zip archive of the folder
            zip_folder_path = shutil.make_archive(base_name = base_name, format = 'zip', root_dir = repo_path)
            zip_files.append(zip_folder_path)
            logging.info(f"Created intermediate zip archive at {zip_folder_path}")
    except Exception as e:
        logging.exception(f"Step 9 failed: Error while creating intermediate zip files for {variant_id}: {e}")
        logging.error("Execute Step 9 in exercises.py")
        return False

    if missing_repos:
        logging.error(f"Step 9 failed: {len(missing_repos)} required folder(s) missing for variant {variant_id}: {', '.join(missing_repos)} — skipping ZIP creation.")
        logging.error("Execute Step 4 in exercises.py to recreate variants, then execute Step 9")
        return False

    # Overwrite problem statement, exercise ID, course ID, title and shortName in the config file
    p_s_file_path = os.path.join(variant_path, "problem-statement.md")
    problem_statement_content = None
    if os.path.exists(p_s_file_path):
        problem_statement_content = read_problem_statement(p_s_file_path)

    config_file_path = os.path.join(variant_path, config_file)
    try:
        logging.info("Overwriting problem statement, exercise ID, course ID, title and shortName in the config file.")
        with open(config_file_path, 'r', encoding='utf-8') as cf:
            exercise_details: Dict[str, Any] = json.load(cf)

            exercise_details['id'] = None
            if problem_statement_content is not None:
                exercise_details['problemStatement'] = problem_statement_content

            course_name = ""
            if 'course' in exercise_details:
                exercise_details['course']['id'] = course_id
                course_name = exercise_details['course'].get('shortName', '')
            elif 'exerciseGroup' in exercise_details:
                # For exam exercises, course is nested under exerciseGroup.exam.course
                if 'exam' in exercise_details['exerciseGroup'] and 'course' in exercise_details['exerciseGroup']['exam']:
                    exercise_details['exerciseGroup']['exam']['course']['id'] = course_id
                    course_name = exercise_details['exerciseGroup']['exam']['course'].get('shortName', '')

            exercise_name = exercise_details.get('title', 'Untitled')
            # Strip any existing variant_id prefix so reruns don't stack duplicates
            prefix = f"{variant_id} - "
            while exercise_name.startswith(prefix):
                exercise_name = exercise_name[len(prefix):]
            exercise_details['title'] = f"{variant_id} - {exercise_name}"

            exercise_details['shortName'] = sanitize_exercise_name(exercise_name, int(variant_id))
            exercise_details["projectKey"] = f"{variant_id}{course_name}{exercise_details['shortName']}"


        with open(config_file_path, 'w', encoding='utf-8') as cf:
            json.dump(exercise_details, cf, indent=4)
            logging.info(f"Updated programming exercise details in {config_file_path}")
    except OSError as e:
        logging.error(f"Step 9 failed: Failed to read exercise JSON file at {config_file_path}: {e}")
        logging.error("Execute Step 9 in exercises.py")
        return False
    zip_files.append(config_file_path)

    # Create the final zip file containing all parts
    with zipfile.ZipFile(exercise_zip_path, 'w', zipfile.ZIP_DEFLATED) as zipf:
        for file in zip_files:
            if 'template' in file: # Rename template zip to exercise zip
                new_name = os.path.join(variant_path, f"{variant_id}-exercise.zip")
                os.rename(file, new_name)
                logging.info(f"Renamed {os.path.basename(file)} to {os.path.basename(new_name)}")
                arcname = os.path.basename(new_name)
                zipf.write(new_name, arcname=arcname)
                logging.info(f"Added {os.path.basename(new_name)} to final zip as {arcname}.")
                continue

            if os.path.basename(file).lower() == config_file.lower(): # rename exercise-details.json to Exercise-Details.json
                arcname = "Exercise-Details.json"
                zipf.write(file, arcname=arcname)
                logging.info(f"Added {os.path.basename(file)} as {arcname} in final zip.")
                continue

            arcname = os.path.basename(file)
            zipf.write(file, arcname=arcname)
            logging.info(f"Added {os.path.basename(file)} to final zip as {arcname}.")
    logging.info(f"Zip file created at {exercise_zip_path}")
    zip_files.append(os.path.join(variant_path, f"{variant_id}-exercise.zip"))

    logging.info("Cleaning up intermediate zip files...")
    for temp_zip in zip_files:
        if temp_zip.endswith('.zip') and os.path.exists(temp_zip):
            os.remove(temp_zip)
            logging.info(f"Removed temporary zip file: {os.path.basename(temp_zip)}")
    return True

def convert_version_variants_to_zip(course_id: int) -> None:
    """
    Converts all exercise variants for specific VERSION defined in config.ini/COURSE_EXERCISES and VERSION into ZIP files.

    Iterates over all courses and exercises in the current DATASET_VERSION,
    retrieves the course ID from the server, and calls :func:`convert_variant_to_zip`
    for each variant directory found.

    :param int course_id: The ID of the course to which the exercises belong.
    :raises SystemExit: if the course ID cannot be retrieved.
    """
    zip_to_create = COURSE_EXERCISES.get(DATASET_VERSION, {})
    pecv_bench_dir = get_pecv_bench_dir()

    succeeded = 0
    failed = 0
    failed_labels: List[str] = []

    for course, exercises in zip_to_create.items():
        for exercise in exercises:
            variants_dir = os.path.join(pecv_bench_dir, "data", DATASET_VERSION, course, exercise, "variants")
            if not os.path.exists(variants_dir):
                logging.warning(f"Variants directory not found, skipping: {variants_dir}")
                continue
            variant_ids = [v for v in os.listdir(variants_dir) if os.path.isdir(os.path.join(variants_dir, v))]
            logging.info(f"Converting {len(variant_ids)} variant(s) for {DATASET_VERSION}/{course}/{exercise}...")
            for variant_id in variant_ids:
                label = f"{course}/{exercise}/{variant_id}"
                ok = convert_variant_to_zip(pecv_bench_dir, DATASET_VERSION, course, exercise, variant_id, course_id)
                if ok:
                    succeeded += 1
                    logging.info(f"[OK]   {label}")
                else:
                    failed += 1
                    failed_labels.append(label)
                    logging.error(f"[FAIL] {label}")

    total = succeeded + failed
    logging.info(f"ZIP conversion: {succeeded}/{total} variants converted successfully.")
    if failed_labels:
        logging.error(f"Step 9 failed: {failed} variant(s) failed ZIP creation:\n" + "\n".join(f"  - {v}" for v in sorted(failed_labels)))
        logging.error("Execute Step 4 in exercises.py to recreate variants, then execute Step 9")

def import_exercise_variant_request(session: requests.Session,
                                                server_url: str,
                                                course_id: int,
                                                pecv_bench_dir: str,
                                                version: str,
                                                course: str,
                                                exercise: str,
                                                variant_id: str) -> bool:
    """
    Imports a programming exercise variant to the Artemis server.

    POST /api/programming-courses/{courseId}/programming-exercises/import-from-file

    :param requests.Session session: The active requests Session object.
    :param str server_url: The base URL of the Artemis server.
    :param int course_id: The ID of the course where the exercise will be imported.
    :param str pecv_bench_dir: The path to the pecv-bench directory.
    :param str version: The dataset version.
    :param str course: The course identifier.
    :param str exercise: The exercise identifier.
    :param str variant_id: The variant identifier.
    :return: Boolean whether import was successful.
    :rtype: bool
    """
    url: str = f"{server_url}/programming/courses/{course_id}/programming-exercises/import-from-file"
    variant_dir = os.path.join(pecv_bench_dir, "data", version, course, exercise, "variants", variant_id)
    config_file = os.path.join(variant_dir, "exercise-details.json")
    exercise_zip = os.path.join(variant_dir, f"{variant_id}-FullExercise.zip")

    try:
        with open(config_file, 'r', encoding='utf-8') as cnfg_file:
            exercise_details: Dict[str, Any] = json.load(cnfg_file)
        exercise_details_str = json.dumps(exercise_details)
        logging.info(f"Loaded programming exercise details from {config_file}")
    except OSError as e:
        logging.error(f"Step 10 failed: Failed to read exercise JSON file at {config_file}: {e}")
        logging.error("Execute Step 9 in exercises.py to recreate ZIPs, then execute Step 10")
        return False

    logging.info(f"Preparing to import exercise: {exercise_details.get('title', 'Untitled')}")
    try:
        with open(exercise_zip, 'rb') as ex_zip:
            exercise_zip_file = ex_zip.read()
            logging.info(f"Loaded programming exercise ZIP file from {exercise_zip}")
    except OSError as e:
        logging.error(f"Step 10 failed: Failed to read exercise ZIP file at {exercise_zip}: {e}")
        logging.error("Execute Step 9 in exercises.py to recreate ZIPs, then execute Step 10")
        return False

    files_payload = {
        'programmingExercise': (
            'Exercise-Details.json',
            exercise_details_str,
            'application/json'
        ),
        'file': (
            os.path.basename(exercise_zip),
            exercise_zip_file,
            'application/zip'
        )
    }

    body, content_type = urllib3.filepost.encode_multipart_formdata(files_payload)
    logging.info("Multipart form-data body and content type prepared.")

    headers = {
        "Content-Type": content_type
        }

    logging.info(f"Sending request to: {url}")

    response: requests.Response = session.post(url, data=body, headers=headers)

    if response.status_code == 200:
        logging.info(f"Imported programming exercise {exercise_details.get('title', 'Untitled')} successfully")
        return True
    else:
        logging.error(f"Step 10 failed: Failed to import {exercise_details.get('title', 'Untitled')}; Status code: {response.status_code}\nResponse content: {response.text}")
        logging.error("Execute Step 10 in exercises.py")
        return False

def import_exercise_variants(session: requests.Session, course_id: int) -> None:
    """
    Imports all programming exercise (their variants) defined in config.ini/COURSE_EXERCISES into the Artemis server to COURSE_NAME.

    COURSE_NAME is specified in config.ini.

    Uses multithreading to speed up the import process.

    :param requests.Session session: The active requests Session object.
    :param int course_id: The ID of the course where the exercises will be imported.
    :return: None
    :rtype: None
    """
    exercises_to_import = COURSE_EXERCISES.get(DATASET_VERSION, {})
    pecv_bench_dir = get_pecv_bench_dir()

    imported_count = 0
    failed_count = 0
    failed_variant_labels: List[str] = []

    logging.info(f"Preparing to import variants for {sum(len(ex) for ex in exercises_to_import.values())} exercises across {len(exercises_to_import)} courses using {MAX_THREADS} threads")
    with ThreadPoolExecutor(max_workers=MAX_THREADS) as executor:
        future_to_variant_label = {}
        for course, exercises in exercises_to_import.items():
            for exercise in exercises:
                variants_dir = os.path.join(pecv_bench_dir, "data", DATASET_VERSION, course, exercise, "variants")
                if not os.path.exists(variants_dir):
                    logging.warning(f"Variants folder not found: {variants_dir}")
                    continue

                for variant_id in os.listdir(variants_dir):
                    variant_dir = os.path.join(variants_dir, variant_id)
                    if not os.path.isdir(variant_dir):
                        logging.warning(f"Variant directory not found: {variant_dir}")
                        continue

                    import_future = executor.submit(
                        import_exercise_variant_request,
                        session,
                        SERVER_URL,
                        course_id,
                        pecv_bench_dir,
                        DATASET_VERSION,
                        course,
                        exercise,
                        variant_id
                    )
                    future_to_variant_label[import_future] = f"{course}/{exercise}/{variant_id}"

        logging.info(f"Total variants to import: {len(future_to_variant_label)}")

        for import_future in as_completed(future_to_variant_label):
            variant_label = future_to_variant_label[import_future]
            try:
                success = import_future.result()
                if success:
                    imported_count += 1
                    logging.info(f"[OK]   {variant_label}")
                else:
                    failed_count += 1
                    failed_variant_labels.append(variant_label)
                    logging.error(f"[FAIL] {variant_label}")
            except Exception as e:
                failed_count += 1
                failed_variant_labels.append(variant_label)
                logging.exception(f"[FAIL] {variant_label} — thread error: {e}")

    logging.info(f"Imported {imported_count}/{len(future_to_variant_label)} variants into course ID {course_id}.")
    if failed_variant_labels:
        logging.error(f"Step 10 failed: Failed to import {failed_count} variant(s):\n" + "\n".join(f"  - {v}" for v in sorted(failed_variant_labels)))
        logging.error("Execute Step 10 in exercises.py to retry the failed imports")


if __name__ == "__main__":
    # This file can be executed independently
    # PART 1 -> COURSE.PY -> PART 2


    # ======= PART 1 ================

    logging.info("Step 1: Getting PECV-Bench directories from config")
    pecv_bench_dir = get_pecv_bench_dir()
    pecv_bench_dataset_dir = get_pecv_bench_dataset_dir()

    logging.info("Step 2: Cloning pecv-bench and pecv-bench-dataset repositories")
    clone_pecv_bench(pecv_bench_dir)
    clone_pecv_bench_dataset(pecv_bench_dataset_dir)

    logging.info("Step 3: Installing pecv-bench dependencies")
    install_pecv_bench_dependencies(pecv_bench_dir)

    logging.info("Step 4: Creating exercise variants")
    create_pecv_bench_version_variants()


    # ======= PART 2 ================
    # NOTE: Steps 9-10 require a session and course id - always use Steps 5-6 and 8 together with them.

    # logging.info("Step 5: Creating session")
    # session = requests.Session()
    # logging.info("Step 6: Logging in as admin")
    # login_as_admin(session=session)
    # logging.info("Step 8: Retrieving Hyperion Benchmark Course ID")
    # course_id = get_course_id_request(session=session)

    # logging.info("Step 9: Converting variants to zip files")
    # convert_version_variants_to_zip(course_id=course_id)

    # logging.info("Step 10: Importing exercise variants")
    # import_exercise_variants(session=session, course_id=course_id)

