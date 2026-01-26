import json
import os
import re
import requests
import zipfile
import shutil
import urllib3
import random
import string
from logging_config import logging
from typing import Dict, Any, List, Tuple
from requests import Session


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

def __read_problem_statement(file_path: str) -> str:
    """
    Reads a markdown file and returns its content as a single string.

    :param str file_path: The path to the markdown file.
    :return: The content of the file as a string.
    :rtype: str
    """
    with open(file_path, 'r', encoding='utf-8') as file:
        content = file.read()

    return content

def convert_variant_to_zip(variant_path: str, course_id: int) -> bool:
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
    variant_id = os.path.basename(variant_path)  # 001
    exercise_zip_filename = f"{variant_id}-FullExercise.zip"
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
    problem_statement_file_path = os.path.join(variant_path, "problem-statement.md")
    if os.path.exists(problem_statement_file_path):
        problem_statement_content = __read_problem_statement(problem_statement_file_path)

    config_file_path = os.path.join(variant_path, config_file)
    try:
        logging.info("Overwriting problem statement, exercise ID, course ID, title and shortName in the config file.")
        with open(config_file_path, 'r') as cf:
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


        with open(config_file_path, 'w') as cf:
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

def import_programming_exercise_request(session: Session, course_id: int, server_url: str, variant_folder_path: str) -> requests.Response:
    """
    Imports a programming exercise to the Artemis server using a multipart/form-data request.

    The request includes two parts:
    1. 'programmingExercise': The configuration JSON (metadata).
    2. 'file': The exercise content as a ZIP archive.

    :param Session session: The active requests Session object.
    :param int course_id: The ID of the course where the exercise will be imported.
    :param str server_url: The base URL of the Artemis server.
    :param str variant_folder_path: The path to the folder containing the exercise variant.
    :return: The JSON response from the server representing the newly created exercise object, or ``None`` if the import failed.
    :rtype: requests.Response or None
    """
    url: str = f"{server_url}/programming/courses/{course_id}/programming-exercises/import-from-file"

    variant_id = os.path.basename(variant_folder_path)
    config_file_path = os.path.join(variant_folder_path, "exercise-details.json")
    exercise_zip_path = os.path.join(variant_folder_path, f"{variant_id}-FullExercise.zip")

    try:
        with open(config_file_path, 'r') as config_file:
            exercise_details: Dict[str, Any] = json.load(config_file)
        exercise_details_str = json.dumps(exercise_details)
        logging.info(f"Loaded programming exercise details from {config_file_path}")
    except OSError as e:
        logging.error(f"Failed to read programming exercise JSON file at {config_file_path}: {e}")
        return None

    logging.info(f"Preparing to import exercise: {exercise_details.get('title', 'Untitled')}")
    try:
        with open(exercise_zip_path, 'rb') as file:
            exercise_zip_file = file.read()
            logging.info(f"Loaded programming exercise ZIP file from {exercise_zip_path}")
    except OSError as e:
        logging.error(f"Failed to read programming exercise ZIP file at {exercise_zip_path}: {e}")
        return None

    files_payload = {
        'programmingExercise': (
            'Exercise-Details.json',
            exercise_details_str,
            'application/json'
        ),
        'file': (
            os.path.basename(exercise_zip_path),
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
        return response.json()
    else:
        logging.error(f"Failed to import programming exercise; Status code: {response.status_code}\nResponse content: {response.text}")
        return None

def consistency_check_request(session: Session, exercise_server_id: int, server_url: str, exercise_variant_local_id: str) -> Dict[str, Any]:
    """
    Server request, to check the consistency of the programming exercise with Hyperion System.

    :param Session session: The active requests Session object.
    :param int exercise_server_id: The ID of the programming exercise on the server.
    :param str server_url: The base URL of the Artemis server.
    :param str exercise_variant_local_id: The local variant identifier (e.g., 'H01E01-Lectures:001').
    :return: A dictionary containing consistency issues, or ``None`` if the request failed.
    :rtype: Dict[str, Any] or None
    """
    logging.info(f"[{exercise_variant_local_id}] 		Starting consistency check for programming exercise ID: {exercise_server_id}")

    url: str = f"{server_url}/hyperion/programming-exercises/{exercise_server_id}/consistency-check"

    response: requests.Response = session.post(url)

    if response.status_code == 200:
        logging.info(f"[{exercise_variant_local_id}] Finished consistency check for programming exercise ID: {exercise_server_id}")
        return response.json()
    else:
        logging.error(f"Failed to check consistency for programming exercise ID {exercise_server_id}; Status code: {response.status_code}\nResponse content: {response.text}")
        return None

def consistency_check_variant_io(session: Session, server_url: str, exercise_variant_local_id: str, exercise_server_id: int, results_dir: str, course: str, run_id: str) -> str:
    """
    Worker function that triggers ``consistency_check_request`` and saves the result for a SINGLE variant.

    :param Session session: The active requests Session object.
    :param str server_url: The base URL of the Artemis server.
    :param str exercise_variant_local_id: The local variant identifier (e.g., 'H01E01-Lectures:001').
    :param int exercise_server_id: The ID of the programming exercise on the server.
    :param str results_dir: The directory where the result JSON file will be saved.
    :param str course: The course identifier.
    :param str run_id: The identifier for the current run.
    :return: A status string indicating success ('success') or failure ('error', 'Skipped ...').
    :rtype: str
    """
    if exercise_server_id is None:
        logging.error(f"Skipping consistency check for variant {exercise_variant_local_id} due to missing exercise ID.")
        return f"Skipped {exercise_variant_local_id} due to missing exercise ID"

    consistency_issue = consistency_check_request(session=session, exercise_server_id=exercise_server_id, server_url=server_url, exercise_variant_local_id=exercise_variant_local_id)

    exercise = exercise_variant_local_id.split(":")[0]
    variant = exercise_variant_local_id.split(":")[1]
    # Data Enrichment
    if consistency_issue is not None:
        consistency_issue["case_id"] = f"{course}/{exercise}/{variant}"
        consistency_issue["run_id"] = run_id

    # File Write (I/O)
    file_path = os.path.join(results_dir, exercise, f"{variant}.json")
    try:
        with open(file_path, "w") as file:
            json.dump(consistency_issue, file, indent=4)
        return f"[{exercise_variant_local_id}] success"
    except Exception as e:
        logging.exception(f"Failed to write file for {exercise_variant_local_id}: {e}")
        return f"[{exercise_variant_local_id}] error"

def convert_base_exercise_to_zip(exercise_path: str, course_id: int) -> None:
    """
    Converts a base programming exercise (no variants) into a ZIP file using a random unique ID.
    """
    base_name = os.path.basename(exercise_path)
    repo_types: List[str] = ["solution", "template", "tests"]
    config_file: str = "exercise-details.json"
    variant_id = 0
    exercise_zip_filename = f"{base_name}-FullExercise.zip"
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
        with open(config_file_path, 'r') as cf:
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
        with open(config_file_path, 'w') as cf:
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