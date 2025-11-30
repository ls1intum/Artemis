import json
import os
import sys
import re
import requests
import zipfile
import shutil
import urllib3
from tracemalloc import start
from logging_config import logging
from typing import Dict, Any, List, Tuple
from requests import Session


def sanitize_exercise_name(exercise_name: str, short_name_index: int) -> str:
    """
    Sanitize the exercise name to create a valid short name.
    Example: "H01E01 - Lectures" -> "H01E01Lectures1"
    """
    valid_short_name = re.sub(r'[^a-zA-Z0-9]', '', exercise_name)
    if not valid_short_name or not valid_short_name[0].isalpha():
        valid_short_name = f"A{valid_short_name}"
    return f"{valid_short_name}{short_name_index}"

def read_problem_statement(file_path: str) -> str:
    """
    Reads a markdown file and returns its content as a single string.
    """
    with open(file_path, 'r', encoding='utf-8') as file:
        content = file.read()
    
    return content

def convert_variant_to_zip(variant_path: str, course_id: int) -> bool:
    """
    Convert the programming exercise located at variant_path into a ZIP file.

    variant_path: ../../pecv-bench/data/{course}/{exercise}/variants/{variant_id}

    Renames template to exercise and overwrites exercise ID, course ID, title and shortName in the config file.
    It stores the final ZIP file in the same directory as the variant with following structure:
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
            |-- Exercise-Details.json
    """

    repo_types: List[str] = ["solution", "template", "tests"]
    config_file: str = "Exercise-Details.json"
    variant_id = os.path.basename(variant_path)  # 001
    exercise_zip_filename = f"{variant_id}-FullExercise.zip" #001-Exercise.zip
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
        problem_statement_content = read_problem_statement(problem_statement_file_path)

    config_file_path = os.path.join(variant_path, config_file)
    try:
        logging.info("Overwriting problem statement, exercise ID, course ID, title and shortName in the config file.")
        with open(config_file_path, 'r') as config_file:
            exercise_details: Dict[str, Any] = json.load(config_file)
            
            exercise_details['id'] = None
            
            exercise_details['problemStatement'] = problem_statement_content
            exercise_details['course']['id'] = course_id
            
            exercise_name = exercise_details.get('title', 'Untitled')
            exercise_details['title'] = f"{variant_id} - {exercise_details.get('title', 'Untitled')}"
            
            exercise_details['shortName'] = sanitize_exercise_name(exercise_name, int(variant_id))
            exercise_details["projectKey"] = f"{variant_id}ITP2425{exercise_details['shortName']}"
            

        with open(config_file_path, 'w') as config_file:
            json.dump(exercise_details, config_file, indent=4)
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
                logging.info(f"Added {new_name} to final zip as {arcname}.")
                continue

            arcname = os.path.basename(file)
            zipf.write(file, arcname=arcname)
            logging.info(f"Added {file} to final zip as {arcname}.")
    logging.info(f"Zip file created at {exercise_zip_path}")
    zip_files.append(os.path.join(variant_path, f"{variant_id}-exercise.zip"))

    logging.info("Cleaning up intermediate zip files...")
    for temp_zip in zip_files:
        if temp_zip.endswith('.zip') and os.path.exists(temp_zip):
            os.remove(temp_zip)
            logging.info(f"Removed temporary zip file: {os.path.basename(temp_zip)}")
    return True

def import_programming_exercise(session: Session, course_id: int, server_url: str, variant_folder_path: str) -> requests.Response:
    """
    Imports a programming exercise to the Artemis server using a multipart/form-data request.
    
    The request includes two parts:
    1. 'programmingExercise': The configuration JSON (metadata).
    2. 'file': The exercise content as a ZIP archive.

    Returns the JSON response from the server (the newly created exercise object).
    """
    url: str = f"{server_url}/programming/courses/{course_id}/programming-exercises/import-from-file"
    
    variant_id = os.path.basename(variant_folder_path)
    config_file_path = os.path.join(variant_folder_path, "Exercise-Details.json") 
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
    

def check_exercise_consistency(session: Session, exercise_server_id: int, server_url: str, exercise_variant_local_id: str):
    """Check the consistency of the programming exercise with Hyperion System.
    
    returns the consistency issues as a list of dictionaries along with the programming exercise ID.
    """
    logging.info(f"[{exercise_variant_local_id}] \t\tStarting consistency check for programming exercise ID: {exercise_server_id}")
    
    url: str = f"{server_url}/hyperion/programming-exercises/{exercise_server_id}/consistency-check"
    
    response: requests.Response = session.post(url)

    if response.status_code == 200:
        logging.info(f"[{exercise_variant_local_id}] Finished consistency check for programming exercise ID: {exercise_server_id}")
        return response.json()
    else:
        logging.error(f"Failed to check consistency for programming exercise ID {exercise_server_id}; Status code: {response.status_code}\nResponse content: {response.text}")
        return None


def process_variant_consistency_check(session: Session, server_url: str, exercise_variant_local_id: str, exercise_server_id: int, results_dir: str, course: str, run_id: str) -> str:
    """
    Worker function to check consistency and save result for a single variant.
    exercise_variant_id: 'H01E01-Lectures:001'
    """
    if exercise_server_id is None:
        logging.error(f"Skipping consistency check for variant {exercise_variant_local_id} due to missing exercise ID.")
        return f"Skipped {exercise_variant_local_id} due to missing exercise ID"

    # 1. Network Request
    consistency_issue = check_exercise_consistency(session=session, exercise_server_id=exercise_server_id, server_url=server_url, exercise_variant_local_id=exercise_variant_local_id)
    
    exercise = exercise_variant_local_id.split(":")[0]
    variant = exercise_variant_local_id.split(":")[1]
    # 2. Data Enrichment
    if consistency_issue is not None:
        consistency_issue["case_id"] = f"{course}/{exercise}/{variant}"
        consistency_issue["run_id"] = run_id

    # 3. File Write (I/O), different threads writing to different files is thread-safe
    file_path = os.path.join(results_dir, exercise, f"{variant}.json")
    try:
        with open(file_path, "w") as file:
            json.dump(consistency_issue, file, indent=4)
        return f"[{exercise_variant_local_id}] success"
    except Exception as e:
        logging.exception(f"Failed to write file for {exercise_variant_local_id}: {e}")
        return f"[{exercise_variant_local_id}] error"