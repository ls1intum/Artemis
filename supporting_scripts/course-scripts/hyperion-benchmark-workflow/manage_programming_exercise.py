import json
import os
import sys
import re
from tracemalloc import start

import requests
import zipfile
import shutil

import urllib3
from logging_config import logging
from typing import Dict, Any, List
from requests import Session

exercise_Ids: list[int] = []

def sanitize_exercise_name(exercise_name: str, short_name_index: int) -> str:
    """Sanitize the exercise name to create a valid short name."""
    valid_short_name = re.sub(r'[^a-zA-Z0-9]', '', exercise_name)
    if not valid_short_name or not valid_short_name[0].isalpha():
        valid_short_name = f"A{valid_short_name}"
    return f"{valid_short_name}{short_name_index}"

def create_programming_exercise(session: Session, course_id: int, server_url: str, exercises_to_create: int, exercise_name: str) -> None:
    """Create multiple programming exercises for the course."""
    for i in range(exercises_to_create):
        url: str = f"{server_url}/programming/programming-exercises/setup"
        headers: Dict[str, str] = {"Content-Type": "application/json"}
        short_name_index: int = i + 1

        short_name = sanitize_exercise_name(exercise_name, short_name_index)

        default_programming_exercise: Dict[str, Any] = {
            "type": "programming",
            "title": f"{exercise_name}",
            "shortName": short_name,
            "course": {"id": course_id},
            "programmingLanguage": "JAVA",
            "projectType": "PLAIN_GRADLE",
            "allowOnlineEditor": True,
            "allowOfflineIde": True,
            "maxPoints": 100,
            "assessmentType": "AUTOMATIC",
            "packageName": "de.tum.in.www1.example",
            "staticCodeAnalysisEnabled": False,
            "buildConfig": {
                "buildScript": "#!/usr/bin/env bash\nset -e\n\ngradle () {\n  echo '⚙️ executing gradle'\n  chmod +x ./gradlew\n  ./gradlew clean test\n}\n\nmain () {\n  gradle\n}\n\nmain \"${@}\"\n",
                "checkoutSolutionRepository": False,
            },
        }

        response: requests.Response = session.post(url, json=default_programming_exercise, headers=headers)

        if response.status_code == 201:
            logging.info(f"Created programming exercise {default_programming_exercise['title']} successfully")
            exercise_Ids.append(response.json().get('id'))
        elif response.status_code == 400:
            logging.info(f"Programming exercise with shortName {default_programming_exercise['shortName']} already exists. Please provide the exercise IDs in the config file and set create_exercises to FALSE.")
            #run_cleanup()
            sys.exit(0)
        else:
            raise Exception(f"Could not create programming exercise; Status code: {response.status_code}\nResponse content: {response.text}")

def convert_variant_to_zip(variant_path: str, course_id: int) -> None:
    """Convert the programming exercise located at variant_path into a ZIP file.
    variant_path: ../../pecv-bench/data/{course}/{exercise}/variants/{variant_id}
    """

    REPO_TYPES: List[str] = ["solution", "template", "tests"]
    CONFIG_FILE: str = "Exercise-Details.json"
    VARIANT_ID = os.path.basename(variant_path)  # 001
    

    exercise_zip_filename = f"{VARIANT_ID}-FullExercise.zip" #001-Exercise.zip
    exercise_zip_path = os.path.join(variant_path, exercise_zip_filename) #...001/001-FullExercise.zip
    logging.info(f"Final zip file: {exercise_zip_filename} will be created at {exercise_zip_path}")
    zip_files = []
    try:
        for repo_type in REPO_TYPES:
            repo_path = os.path.join(variant_path, repo_type) #001/solution
            repo_name = f"{VARIANT_ID}-{repo_type}"  #001-solution
            base_name = os.path.join(variant_path, repo_name)
            if not os.path.exists(repo_path):
                logging.error(f"Required folder {repo_type} does not exist in the variant path {variant_path}.")
                continue
            zip_folder_path = shutil.make_archive(base_name = base_name, format = 'zip', root_dir = repo_path)
            logging.info(f"Created intermediate zip archive at {zip_folder_path}")
            zip_files.append(zip_folder_path)
    except Exception as e:
        logging.error(f"Error while creating intermediate zip files: {e}")
    

    CONFIG_FILE_PATH = os.path.join(variant_path, CONFIG_FILE)
    try:
        with open(CONFIG_FILE_PATH, 'r') as config_file:
            exercise_details: Dict[str, Any] = json.load(config_file)
            exercise_details['id'] = None
            exercise_details['course']['id'] = course_id
            EXERCISE_NAME = exercise_details.get('title', 'Untitled')
            exercise_details['title'] = f"{VARIANT_ID} - {exercise_details.get('title', 'Untitled')}"
            exercise_details['shortName'] = sanitize_exercise_name(EXERCISE_NAME, int(VARIANT_ID))
            exercise_details["projectKey"] = f"{VARIANT_ID}ITP2425H01E01"
            logging.info("Overwriting exercise ID, course ID, title and shortName in the config file.")
        with open(CONFIG_FILE_PATH, 'w') as config_file:
            json.dump(exercise_details, config_file, indent=4)
            logging.info(f"Updated programming exercise details in {CONFIG_FILE_PATH}")
    except OSError as e:
        raise Exception(f"Failed to read programming exercise JSON file at {CONFIG_FILE_PATH}: {e}")
    
    zip_files.append(CONFIG_FILE_PATH)

    with zipfile.ZipFile(exercise_zip_path, 'w', zipfile.ZIP_DEFLATED) as zipf:
        for file in zip_files:
            if 'template' in file:
                new_name = os.path.join(variant_path, f"{VARIANT_ID}-exercise.zip")
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
    zip_files.append(os.path.join(variant_path, f"{VARIANT_ID}-exercise.zip"))

    logging.info("Cleaning up intermediate zip files...")
    for temp_zip in zip_files:
        if temp_zip.endswith('.zip') and os.path.exists(temp_zip):
            os.remove(temp_zip)
            logging.info(f"Removed temporary zip file: {os.path.basename(temp_zip)}")
    
    logging.info("Verifying contents of the final zip file...")
    with zipfile.ZipFile(exercise_zip_path, 'r') as zipf:
        zip_contents = zipf.namelist()
        logging.info(f"Contents of {exercise_zip_filename}: {zip_contents}")

def import_programming_exercise(session: Session, course_id: int, server_url: str, variant_folder_path: str) -> requests.Response:
    """
    Imports a programming exercise to the Artemis server using a multipart/form-data request.
    
    The request includes two parts:
    1. 'programmingExercise': The configuration JSON (metadata).
    2. 'file': The exercise content as a ZIP archive.

    Returns the JSON response from the server (the newly created exercise object).
    """
    url: str = f"{server_url}/programming/courses/{course_id}/programming-exercises/import-from-file"
    VARIANT_ID = os.path.basename(variant_folder_path)
    CONFIG_FILE_PATH = os.path.join(variant_folder_path, "Exercise-Details.json") 
    EXERCISE_ZIP_PATH = os.path.join(variant_folder_path, f"{VARIANT_ID}-FullExercise.zip")

    logging.info("Verifying contents of the final zip file...")
    with zipfile.ZipFile(EXERCISE_ZIP_PATH, 'r') as zipf:
        zip_contents = zipf.namelist()
        logging.info(f"Contents of {EXERCISE_ZIP_PATH}: {zip_contents}")
    try:
        with open(CONFIG_FILE_PATH, 'r') as config_file:
            exercise_details: Dict[str, Any] = json.load(config_file)
            logging.info(f"Set exercise ID to None for import und updated course ID")
        exercise_details_str = json.dumps(exercise_details)
        logging.info(f"Loaded programming exercise details from {CONFIG_FILE_PATH}")
    except OSError as e:
        logging.error(f"Failed to read programming exercise JSON file at {CONFIG_FILE_PATH}: {e}")
        return None
    
    logging.info(f"Preparing to import exercise: {exercise_details.get('title', 'Untitled')}")
    try:
        with open(EXERCISE_ZIP_PATH, 'rb') as file:
            exercise_zip_file = file.read()
            logging.info(f"Loaded programming exercise ZIP file from {EXERCISE_ZIP_PATH}")
    except OSError as e:
        logging.error(f"Failed to read programming exercise ZIP file at {EXERCISE_ZIP_PATH}: {e}")
        return None
    
    files_payload = {
        'programmingExercise': (
            'Exercise-Details.json',
            exercise_details_str,
            'application/json'
        ),
        'file': (
            os.path.basename(EXERCISE_ZIP_PATH),
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

    if response.ok:
        logging.info(f"Imported programming exercise {exercise_details.get('title', 'Untitled')} successfully")
        return response.json()
    else:
        logging.error(f"Failed to import programming exercise; Status code: {response.status_code}\nResponse content: {response.text}")
        return None
    

def check_consistency(session: Session, programming_exercise_ids: [int], server_url: str) -> Any: #Dict[str, Any]:
    """Check the consistency of the programming exercise."""

    ##api/hyperion/programming-exercises/{programmingExerciseId}/consistency-check
