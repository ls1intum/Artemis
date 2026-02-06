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

from utils import MAX_THREADS, PECV_BENCH_DIR, PECV_BENCH_URL, DATASET_VERSION, COURSE_EXERCISES, SERVER_URL, login_as_admin
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
            # ======= DATASET EXTENSION BRANCH ======
            checkout_pecv_bench_dataset_extension_branch(cwd = pecv_bench_dir)
            # =======================================
            subprocess.run(
                ["git", "pull"],
                cwd=pecv_bench_dir,
                check=True,
            )
            logging.info("Successfully pulled latest changes.")

        except subprocess.CalledProcessError as e:
            logging.error(f"ERROR: Failed to pull updates for {pecv_bench_dir}. Error: {e}")
            sys.exit(1)
    else:
        logging.info(f"Cloning repository from {PECV_BENCH_URL} into {pecv_bench_dir}.")
        try:
            subprocess.run(
                ["git", "clone", PECV_BENCH_URL, pecv_bench_dir],
                check=True,
            )
            logging.info("Successfully cloned the repository.")

            # ====== DATASET EXTENSION BRANCH =======
            checkout_pecv_bench_dataset_extension_branch(pecv_bench_dir)
            # =======================================

        except subprocess.CalledProcessError as e:
            logging.error(f"ERROR: Failed to clone repository from {PECV_BENCH_URL}. Error: {e}")

            sys.exit(1)

def checkout_pecv_bench_dataset_extension_branch(cwd: str) -> None:
    """
    Checkout's to the 'dataset-extension' branch in the pecv-bench repository.
    """
    subprocess.run(
            ["git", "checkout", "dataset-extension"],
            cwd=cwd,
            check=True,
        )
    logging.info("Successfully checkout to latest dataset branch.")

def install_pecv_bench_dependencies(pecv_bench_dir: str) -> None:
    """
    Installs the pecv-bench project in editable mode to get all dependencies.

    :param str pecv_bench_dir: The path to the project directory
    :raises SystemExit: if the pip install command fails
    """
    if not os.path.exists(pecv_bench_dir):
        logging.error(f"ERROR: The specified project path {pecv_bench_dir} does not exist.")
        logging.error("Clone pecv-bench first by calling 'clone_pecv_bench' function.")
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
        logging.error(f"ERROR: Failed to install pecv-bench dependencies from {pecv_bench_dir}.")
        logging.error(f"Pip install stderr: {e.stderr}")
        sys.exit(1)

def check_pecv_bench_setup(pecv_bench_dir: str) -> bool:
    """
    Checks if pecv-bench directory exists and if dependencies are likely installed.
    """
    if not os.path.exists(pecv_bench_dir):
        logging.error(f"PECV-bench directory not found at {pecv_bench_dir}.")
        logging.error("Please run 'clone_pecv_bench' first.")
        return False

    # Simple check if we can import the module (implies installation)
    if pecv_bench_dir not in sys.path:
        sys.path.insert(0, pecv_bench_dir)

    try:
        import cli
        return True
    except ImportError:
        logging.error("Could not import 'cli' from pecv-bench. Dependencies might not be installed.")
        logging.error("Please run 'install_pecv_bench_dependencies' first.")
        return False

def create_exercise_variants(version: str, course: str, exercise: str, pecv_bench_dir: str) -> None:
    """
    Imports VariantManager and ExerciseIdentifier from pecv-bench and creates all variants with materialize_variant func.

    Requires that pecv-bench is in sys.path and dependencies are installed.

    This function applies the git patch file to create the variant.

    :param str version: The dataset version
    :param str course: The course identifier
    :param str exercise: The exercise identifier

    :raises Exception: if pecv_bench is not in sys.path
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
        logging.error("ERROR: Failed to import VariantManager and ExerciseIdentifier from pecv-bench.")
        raise e

    try:
        exercise_id = ExerciseIdentifier(course=course, exercise=exercise)
        manager = VariantManager(exercise_id, version=version)

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

def create_exercise_variants_all() -> None:
    """
    Creates all exercise variants for the courses and exercises defined in config.ini/COURSE_EXERCISES.

    This function iterates through the COURSE_EXERCISES dictionary and calls create_exercise_variants
    for each course and exercise combination.
    """
    pecv_bench_dir = get_pecv_bench_dir()
    if not check_pecv_bench_setup(pecv_bench_dir):
        return

    variants_to_create = COURSE_EXERCISES.get(DATASET_VERSION, {})
    for course, exercises in variants_to_create.items():
        for exercise in exercises:
            logging.info(f"Creating variants for {DATASET_VERSION}/{course}/{exercise}...")
            create_exercise_variants(DATASET_VERSION, course, exercise, pecv_bench_dir)

def __sanitize_exercise_name(exercise_name: str, short_name_index: int) -> str:
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

def __read_problem_statement(p_s_file_path: str) -> str:
    """
    Reads a markdown file and returns its content as a single string.

    :param str ps_path: The path to the markdown file.
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

    :param str variant_path: The path to the variant directory (e.g., ``../../pecv-bench/data/{course}/{exercise}/variants/{variant_id}``).
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
    try:
        for repo_type in repo_types:
            repo_name = f"{variant_id}-{repo_type}"  #001-solution
            base_name = os.path.join(variant_path, repo_name)

            repo_path = os.path.join(variant_path, repo_type) #001/solution
            if not os.path.exists(repo_path):
                logging.error(f"Required folder {repo_type} does not exist in the variant path {variant_path}.")
                continue
            # Create a zip archive of the folder
            zip_folder_path = shutil.make_archive(base_name = base_name, format = 'zip', root_dir = repo_path)
            zip_files.append(zip_folder_path)
            logging.info(f"Created intermediate zip archive at {zip_folder_path}")
    except Exception as e:
        logging.exception(f"Error while creating intermediate zip files: {e}")
        return False

    # Overwrite problem statement, exercise ID, course ID, title and shortName in the config file
    p_s_file_path = os.path.join(variant_path, "problem-statement.md")
    if os.path.exists(p_s_file_path):
        problem_statement_content = __read_problem_statement(p_s_file_path)

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
            exercise_details['title'] = f"{variant_id} - {exercise_details.get('title', 'Untitled')}"

            exercise_details['shortName'] = __sanitize_exercise_name(exercise_name, int(variant_id))
            exercise_details["projectKey"] = f"{variant_id}{course_name}{exercise_details['shortName']}"


        with open(config_file_path, 'w', encoding='utf-8') as cf:
            json.dump(exercise_details, cf, indent=4)
            logging.info(f"Updated programming exercise details in {config_file_path}")
    except OSError as e:
        logging.error(f"Failed to read programming exercise JSON file at {config_file_path}: {e}")
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

def convert_variant_to_zip_all(session: requests.Session) -> None:
    zip_to_create = COURSE_EXERCISES.get(DATASET_VERSION, {})
    pecv_bench_dir = get_pecv_bench_dir()

    try:
        course_id = get_course_id_request(session=session)
    except Exception as e:
        logging.error(f"Cannot convert variants to zip: {e}")
        logging.error("Ensure you have run 'create_course_request' successfully first.")
        return

    for course, exercises in zip_to_create.items():
        for exercise in exercises:
            logging.info(f"Creating variants for {DATASET_VERSION}/{course}/{exercise}...")
            variants_dir = os.path.join(pecv_bench_dir, "data", DATASET_VERSION, course, exercise, "variants")
            variants_list_id = os.listdir(variants_dir)
            for variant_id in variants_list_id:
                variant_path = os.path.join(variants_dir, variant_id)
                if os.path.isdir(variant_path):
                    logging.info(f"Converting variant {variant_id} to zip...")
                    convert_variant_to_zip(pecv_bench_dir, DATASET_VERSION, course, exercise, variant_id, course_id)

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

    :param Session session: The active requests Session object.
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
        logging.error(f"Failed to read programming exercise JSON file at {config_file}: {e}")
        return False

    logging.info(f"Preparing to import exercise: {exercise_details.get('title', 'Untitled')}")
    try:
        with open(exercise_zip, 'rb') as ex_zip:
            exercise_zip_file = ex_zip.read()
            logging.info(f"Loaded programming exercise ZIP file from {exercise_zip}")
    except OSError as e:
        logging.error(f"Failed to read programming exercise ZIP file at {exercise_zip}: {e}")
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
    logging.info(f"Multipart form-data body and content type prepared.")

    headers  = {
        "Content-Type": content_type
        }

    logging.info(f"Sending request to: {url}")

    response: requests.Response = session.post(url, data=body, headers=headers)

    if response.status_code == 200:
        logging.info(f"Imported programming exercise {exercise_details.get('title', 'Untitled')} successfully")
        return True
    else:
        logging.error(f"Failed to import programming exercise; Status code: {response.status_code}\nResponse content: {response.text}")
        return False

def import_exercise_variants(session: requests.Session) -> None:
    """
    Imports all programming exercise (their variants) defined in config.ini/COURSE_EXERCISES into the Artemis server to COURSE_NAME.

    COURSE_NAME is specified in config.ini.

    Uses multithreading to speed up the import process.

    :param Session session: The active requests Session object.
    :return: None
    :rtype: None
    """
    exercises_to_import = COURSE_EXERCISES.get(DATASET_VERSION, {})
    pecv_bench_dir = get_pecv_bench_dir()

    try:
        course_id = get_course_id_request(session=session)
    except Exception as e:
        logging.error(f"Cannot import exercises: {e}")
        logging.error("Ensure you have run 'create_course_request' successfully first.")
        return

    #TODO improve logging for which exercises failed to import
    total_variants_imported = 0
    total_variants_failed = 0

    logging.info(f"Preparing to import variants for {sum(len(ex) for ex in COURSE_EXERCISES.values())} exercises across {len(COURSE_EXERCISES)} courses using {MAX_THREADS} threads")
    with ThreadPoolExecutor(max_workers=MAX_THREADS) as executor:
        futures = []
        # submit all tasks
        for course, exercises in exercises_to_import.items():
            for exercise in exercises:
                variants_dir = os.path.join(pecv_bench_dir, "data", DATASET_VERSION, course, exercise, "variants")
                if not os.path.exists(variants_dir):
                    logging.warning(f"Variants folder not found: {variants_dir}")
                    continue

                variants_list_id = os.listdir(variants_dir)
                for variant_id in variants_list_id:
                    variant_id_dir = os.path.join(variants_dir, variant_id)
                    if not os.path.isdir(variant_id_dir):
                        logging.warning(f"Variant ID directory not found: {variant_id_dir}")
                        continue

                    futures.append(executor.submit(
                        import_exercise_variant_request,
                        session,
                        SERVER_URL,
                        course_id,
                        pecv_bench_dir,
                        DATASET_VERSION,
                        course,
                        exercise,
                        variant_id
                    ))

        for future in as_completed(futures):
            try:
                result = future.result()
                if result:
                    total_variants_imported += 1
                    logging.info(f"Imported variant successfully.")
                else:
                    total_variants_failed += 1
                    logging.error(f"Failed to import variant.")
            except Exception as e:
                logging.exception(f"Thread failed with error: {e}")
                return
    logging.info(f"Imported {total_variants_imported} programming exercises into course ID {course_id}.")
    logging.error(f"Failed to import {total_variants_failed} programming exercises into course ID {course_id}.")

# ======= TEST FUNCTIONS =======
def test_convert_base_exercise_to_zip(exercise_path: str, course_id: int) -> None:
    """
    Converts a base programming exercise (no variants) into a ZIP file using a random unique ID.

    :param str exercise_path: The path to the base exercise directory on your computer. Should contain solution/, template/, tests/ folders, problem-statement.md and exercise-details.json file.
    :param int course_id: The ID of the course to which the exercise belongs. Can be obtained via API request (e.g Postman).
    Or simply calling get_course_id_request function after logging in, which will retrieve the course id based on the course name defined in config.ini.

    :return: None
    :rtype: None
    """
    base_name = os.path.basename(exercise_path)
    repo_types: List[str] = ["solution", "template", "tests"]
    config_file: str = "exercise-details.json"
    variant_id = 0
    exercise_zip_filename = f"{variant_id}-FullExercise.zip"
    exercise_zip_path = os.path.join(exercise_path, exercise_zip_filename)

    logging.info(f"Final zip file: {exercise_zip_filename} will be created at {exercise_zip_path}")

    zip_files = []
    try:
        for repo_type in repo_types:
            repo_name = f"{variant_id}-{repo_type}"
            base_name = os.path.join(exercise_path, repo_name)
            repo_path = os.path.join(exercise_path, repo_type)
            if not os.path.exists(repo_path):
                logging.error(f"Required folder {repo_type} does not exist in {exercise_path}.")
                continue
            zip_folder_path = shutil.make_archive(base_name = base_name, format = 'zip', root_dir = repo_path)
            zip_files.append(zip_folder_path)
    except Exception as e:
        logging.exception(f"Error while creating intermediate zip files: {e}")
        return None

    problem_statement_file_path = os.path.join(exercise_path, "problem-statement.md")
    problem_statement_content = None
    if os.path.exists(problem_statement_file_path):
        problem_statement_content = __read_problem_statement(problem_statement_file_path)

    config_file_path = os.path.join(exercise_path, config_file)
    try:
        with open(config_file_path, 'r', encoding='utf-8') as cf:
            exercise_details: Dict[str, Any] = json.load(cf)
            exercise_details['id'] = None
            if problem_statement_content is not None:
                exercise_details['problemStatement'] = problem_statement_content
            course_name = ""
            if 'course' in exercise_details:
                exercise_details['course']['id'] = course_id
                course_name = exercise_details['course'].get('shortName', '')
            exercise_name = exercise_details.get('title', 'Untitled')
            exercise_details['title'] = f"{variant_id} - {exercise_name}"
            exercise_details['shortName'] = __sanitize_exercise_name(exercise_name, int(variant_id))
            exercise_details["projectKey"] = f"{variant_id}{course_name}{exercise_details['shortName']}"
        with open(config_file_path, 'w', encoding='utf-8') as cf:
            json.dump(exercise_details, cf, indent=4)
    except Exception as e:
        logging.error(f"Failed to update config file: {e}")
        return None
    zip_files.append(config_file_path)

    with zipfile.ZipFile(exercise_zip_path, 'w', zipfile.ZIP_DEFLATED) as zipf:
        for file in zip_files:
            if 'template' in file:
                new_name = os.path.join(exercise_path, f"{variant_id}-exercise.zip")
                os.rename(file, new_name)
                zipf.write(new_name, arcname=f"{variant_id}-exercise.zip")
                continue
            arcname = "Exercise-Details.json" if os.path.basename(file).lower() == config_file.lower() else os.path.basename(file)
            zipf.write(file, arcname=arcname)

    zip_files.append(os.path.join(exercise_path, f"{variant_id}-exercise.zip"))
    for temp_zip in zip_files:
        if temp_zip.endswith('.zip') and os.path.exists(temp_zip):
            os.remove(temp_zip)

#TODO
def test_import_exercise_base_request() -> None:
    pass

#TODO
def test_consistency_check() -> None:
    pass
# ==============================

if __name__ == "__main__":
    logging.info("Step 1: Creating session")
    session = requests.Session()

    logging.info("Step 2: Logging in as admin")
    login_as_admin(session=session)

    logging.info("Step 3: geting pecv-bench directory")
    pecv_bench_dir = get_pecv_bench_dir()

    logging.info("Step 4: cloning pecv-bench repository")
    clone_pecv_bench(pecv_bench_dir)

    logging.info("Step 5: installing pecv-bench dependencies")
    install_pecv_bench_dependencies(pecv_bench_dir)

    logging.info("Step 6: creating exercise variants")
    create_exercise_variants_all()

    logging.info("Step 7: converting variants to zip files")
    convert_variant_to_zip_all(session=session)

    #logging.info("Step TEST: converting base exercise to zip file and importing it to Artemis")
    #test_convert_base_exercise_to_zip(exercise_path="/Users/mkh/Desktop/test_function", course_id=22)
    #test_import_exercise_base_request()
    #test_consistency_check()

    logging.info("Step 8: importing exercise variants")
    import_exercise_variants(session=session)