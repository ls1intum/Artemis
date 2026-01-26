from concurrent.futures import ThreadPoolExecutor, as_completed
import os
import sys
import requests
import configparser
import json
import urllib3
import re
import time
from logging_config import logging
from requests import Session
from typing import Dict, Any, Union, List
from utils import COURSE_EXERCISES, MAX_THREADS, PECV_BENCH_REPO_URL, SERVER_URL, clone_pecv_bench, create_exercise_variants, get_pecv_bench_dir, install_pecv_bench_dependencies, login_as_admin
from manage_programming_exercise import process_single_variant_import

"""
DISCLAIMER: Execution Context Sensitivity
This script relies on 'config.ini' being present in the current working directory.
It uses module-level global variables loaded from this configuration.
Ensure this script is executed from the directory containing 'config.ini'.
"""

# Load configuration
config = configparser.ConfigParser()
config.read(['config.ini'])

# Constants from config file
IS_LOCAL_COURSE: bool = config.get('CourseSettings', 'is_local_course').lower() == 'true'  # Convert to boolean
COURSE_NAME: str = config.get('CourseSettings', 'course_name')
SPECIAL_CHARACTERS_REGEX: str = config.get('CourseSettings', 'special_character_regex')


def __parse_course_name_to_short_name() -> str:
    """
    Parse course name to create a short name, removing special characters.

    :return: The parsed short name for the course.
    :rtype: str
    """
    short_name = COURSE_NAME.strip()
    short_name = re.sub(SPECIAL_CHARACTERS_REGEX, '', short_name.replace(' ', ''))

    if len(short_name) > 0 and not short_name[0].isalpha():
        short_name = 'a' + short_name

    return short_name

def create_pecv_bench_course_request(session: Session) -> requests.Response:
    """
    Create a course using the given session.

    POST /core/admin/courses

    :param Session session: The active requests Session object.
    :return: The JSON response containing the created course details, such as ID etc.
    :rtype: requests.Response
    :raises Exception: If course creation fails or if the course cannot be deleted before recreation.
    """
    url = f"{SERVER_URL}/core/admin/courses"
    course_short_name = __parse_course_name_to_short_name()

    default_course = {
        "id": None,
        "title": str(COURSE_NAME),
        "shortName": str(course_short_name),
        "customizeGroupNames": False,
        "studentGroupName": None,
        "teachingAssistantGroupName": None,
        "editorGroupName": None,
        "instructorGroupName": None,
        "courseInformationSharingMessagingCodeOfConduct": None,
        "semester": None,
        "testCourse": None,
        "onlineCourse": False,
        "complaintsEnabled": True,
        "requestMoreFeedbackEnabled": True,
        "maxPoints": None,
        "accuracyOfScores": 1,
        "defaultProgrammingLanguage": None,
        "maxComplaints": 3,
        "maxTeamComplaints": 3,
        "maxComplaintTimeDays": 7,
        "maxComplaintTextLimit": 2000,
        "maxComplaintResponseTextLimit": 2000,
        "maxRequestMoreFeedbackTimeDays": 7,
        "registrationConfirmationMessage": None,
        "unenrollmentEnabled": None,
        "color": None,
        "courseIcon": None,
        "timeZone": None,
        "courseInformationSharingConfiguration": "COMMUNICATION_AND_MESSAGING",
        "enrollmentEnabled": False
    }

    if IS_LOCAL_COURSE:
        default_course["customizeGroupNames"] = True
        default_course["studentGroupName"] = "students"
        default_course["teachingAssistantGroupName"] = "tutors"
        default_course["editorGroupName"] = "editors"
        default_course["instructorGroupName"] = "instructors"

    fields = {
        "course": ('blob.json', json.dumps(default_course), 'application/json')
    }
    body, content_type = urllib3.filepost.encode_multipart_formdata(fields)
    headers = {
        'Content-Type': content_type,
    }

    logging.info(f"Creating course {COURSE_NAME} with shortName {course_short_name}")

    response: requests.Response = session.post(url, data=body, headers=headers)

    if response.status_code == 400:
        logging.error(f"Course with shortName {course_short_name} already exists")

        # Try to delete existing course with retry logic
        course_is_deleted = delete_pecv_bench_course_request(session, course_short_name)

        if course_is_deleted:
            logging.info(f"Waiting 2 seconds for server cleanup before recreation")
            time.sleep(2)

            logging.info(f"RETRYING: Creating course {COURSE_NAME} with shortName {course_short_name}")
            response: requests.Response = session.post(url, data=body, headers=headers)
        else:
            raise Exception(f"Failed to delete existing course {COURSE_NAME} after multiple attempts. Cannot proceed")

    # final check for successful creation
    if response.status_code == 201:
        logging.info(f"Successfully created course '{COURSE_NAME}' (ShortName: {course_short_name})")
        return response.json()
    else:
        raise Exception(
                f"Could not create course {COURSE_NAME}; Status code: {response.status_code}\n"
                f"Double check whether the courseShortName {course_short_name} is valid (e.g. no special characters such as '-')!\n"
                f"Response content: {response.text}")

def delete_pecv_bench_course_request(session: Session, course_short_name: str, max_retries: int = 3) -> bool:
    """
    Delete a course with retry logic using the given session and course ID.

    DELETE /core/admin/courses/{courseId}

    Sometimes it takes multiple scripts reruns to successfully delete a course.
    This approach fixes this.

    :param Session session: The active requests Session object.
    :param str course_short_name: The short name of the course to delete.
    :param int max_retries: Maximum number of deletion attempts. Defaults to 3.
    :return: True if the course was successfully deleted, False otherwise.
    :rtype: bool
    """

    logging.info(f"Attempting to delete course with shortName {course_short_name}")

    course_id = get_pecv_bench_course_id_request(session)

    delete_url = f"{SERVER_URL}/core/admin/courses/{course_id}"
    logging.info(f"Deleting course with ID {course_id} at URL {delete_url}")

    for attempt in range(1, max_retries +1):
        logging.info(f"Deletion attempt {attempt}/{max_retries}")
        try:
            # If server takes longer than 60s, it raises a Timeout exception, triggering the retry logic.
            logging.info(f"Sending DELETE request, it can take around 30 seconds to delete a course")
            deleteCourseResponse: requests.Response = session.delete(delete_url, timeout = 60)
            if deleteCourseResponse.status_code == 200:
                logging.info(f"DELETED: on attempt {attempt} course with shortName {course_short_name} was deleted successfully ")
                return True
            else:
                logging.error(f"Deletion attempt {attempt}/{max_retries} for {course_short_name} failed with status code {deleteCourseResponse.status_code}")
        except Exception as e:
            logging.exception(f"Exception during deletion attempt {attempt}/{max_retries} for {course_short_name}: {e}")

        if attempt < max_retries:
            wait_time = 2 * attempt
            logging.info(f"Waiting {wait_time} seconds before retrying deletion...")
            time.sleep(wait_time)
    logging.error(f"Failed to delete course with shortName {course_short_name} after {max_retries} attempts.")
    return False

def get_pecv_bench_course_id_request(session: Session) -> int:
    """
    Get the course ID for the given course name using the provided session.

    GET /core/courses

    :param Session session: The active requests Session object.
    :return: The ID of the course.
    :rtype: int
    :raises Exception: If the course with the generated short name is not found.
    """
    course_short_name = __parse_course_name_to_short_name()
    courseResponse: requests.Response = session.get(f"{SERVER_URL}/core/courses")
    courses = courseResponse.json()
    for course in courses:
        if course["shortName"] == course_short_name:
            logging.info(f"Found course ID {course['id']} for course shortName {course_short_name}")
            return course["id"]
    raise Exception(f"Course with shortName {course_short_name} not found")

def get_exercise_ids_from_pecv_bench_request(session: Session, course_id: int) -> Dict[str, int]:
    """
    Retrieves programming exercises for a specific course and extracts IDs and Titles.

    :param Session session: The active requests Session object.
    :param int course_id: The ID of the course to retrieve exercises from.
    :return: A list of dictionaries containing 'title' as key and 'id' as value.
    :rtype: Dict[str, int]
    """
    url = f"{SERVER_URL}/programming/courses/{course_id}/programming-exercises"

    try:
        response = session.get(url)

        exercises_data = response.json()

        # dictionary: Key = Title, Value = ID
        exercises_map = {}

        for exercise in exercises_data:
            title = exercise.get("title")
            ex_id = exercise.get("id")

            if title is not None and ex_id is not None:
                exercises_map[title] = ex_id
        return __transform_exercise_json_keys(exercises_map)

    except requests.exceptions.RequestException as e:
        print(f"Error fetching data: {e}")
        return {}
    except ValueError as e:
        print(f"Error parsing JSON: {e}")
        return {}

def __transform_exercise_json_keys(input_dict: Dict[str, int]) -> Dict[str, int]:
    """
    Transforms the keys of the input dictionary into a new format.

    :param Dict[str, int] input_dict: The dictionary with original keys.
    :return: A dictionary with transformed keys.
    :rtype: Dict[str, int]
    """
    transformed_dict = {}

    # Regex to capture: (Number) - (ShortCode) (Optional Hyphen) (Description)
    # Group 1: Number (e.g. 002)
    # Group 2: ShortCode (e.g. H05E01)
    # Group 3: Description (e.g. REST Architectural Style)
    pattern = re.compile(r"^(\d+)\s*-\s*([^\s]+)\s*(?:-\s*)?(.*)$")

    for original_key, value in input_dict.items():
        match = pattern.match(original_key)

        if match:
            seq_num = match.group(1)
            short_code = match.group(2)
            raw_description = match.group(3)

            # Replace spaces in description with underscores
            clean_description = raw_description.replace(" ", "_")

            # Construct new key: Code-Description:Number
            new_key = f"{short_code}-{clean_description}:{seq_num}"

            transformed_dict[new_key] = value
        else:
            # Fallback if pattern doesn't match (keep original or log error)
            print(f"Warning: Could not parse key '{original_key}'")
            transformed_dict[original_key] = value

    return transformed_dict


def setup_pecv_bench_course(session: Session) -> str:
    """
    Sets up the PECV-Bench environment and imports programming exercises.

    This function performs the following steps:
    1. Clones the pecv-bench repository if it does not exist or pulls the latest changes.
    2. Installs necessary pecv-bench dependencies.
    3. Creates all exercise variants based on the configuration.
    4. Creates a new course in Artemis for the benchmark.
    5. Imports each exercise variant as a programming exercise into the course using multiple threads.

    """
    logging.info("Starting PECV-Bench Course Setup Script...")
    login_as_admin(session)

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
                create_exercise_variants(COURSE, EXERCISE)
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
                return
    logging.info(f"Imported {len(programming_exercises)} programming exercises into course ID {course_id}.")
    return pecv_bench_dir