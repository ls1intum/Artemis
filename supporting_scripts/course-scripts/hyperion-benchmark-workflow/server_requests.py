import requests
import configparser
import json
import urllib3
import re
import time
from logging_config import logging
from requests import Session

# Load configuration
config = configparser.ConfigParser()
config.read(['config.ini'])

# Constants from config file
SERVER_URL: str = config.get('Settings', 'server_url')
IS_LOCAL_COURSE: bool = config.get('CourseSettings', 'is_local_course').lower() == 'true'  # Convert to boolean
COURSE_NAME: str = config.get('CourseSettings', 'course_name')
SPECIAL_CHARACTERS_REGEX: str = config.get('CourseSettings', 'special_character_regex')

# =======================from utils.py =======================
CLIENT_URL: str = config.get('Settings', 'client_url')
SERVER_URL: str = config.get('Settings', 'server_url')
ADMIN_USER: str = config.get('Settings', 'admin_user')
ADMIN_PASSWORD: str = config.get('Settings', 'admin_password')

# POST /core/public/authenticate
def login_as_admin(session: requests.Session) -> None:
    """Authenticate as an admin using the provided session.

    POST /core/public/authenticate
    """
    authenticate_user(ADMIN_USER, ADMIN_PASSWORD, session)

def authenticate_user(username: str, password: str, session: requests.Session = requests.Session()) -> requests.Response:
    """Authenticate a user and return the session response."""
    url: str = f"{SERVER_URL}/core/public/authenticate"
    headers: Dict[str, str] = {
        "Content-Type": "application/json"
    }

    payload: Dict[str, Any] = {
        "username": username,
        "password": password,
        "rememberMe": True
    }

    response: requests.Response = session.post(url, json=payload, headers=headers)

    if response.status_code == 200:
        logging.info(f"Authentication successful for user {username}")
    else:
        raise Exception(
            f"Authentication failed for user {username}. Status code: {response.status_code}\n Response content: {response.text}")

    return response
# ========================================================

# SUPPORTING FUNCTIONS FOR COURSE AND EXERCISE MANAGEMENT
def parse_course_name_to_short_name() -> str:
    """Parse course name to create a short name, removing special characters."""
    short_name = COURSE_NAME.strip()
    short_name = re.sub(SPECIAL_CHARACTERS_REGEX, '', short_name.replace(' ', ''))

    if len(short_name) > 0 and not short_name[0].isalpha():
        short_name = 'a' + short_name

    return short_name

# POST /core/admin/courses
def create_pecv_bench_course(session: Session) -> requests.Response:
    """Create a course using the given session.

    POST /core/admin/courses
    """
    url = f"{SERVER_URL}/core/admin/courses"
    course_short_name = parse_course_name_to_short_name()

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

        course_is_deleted = delete_pecv_bench_course(session, course_short_name)

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

# DELETE /core/admin/courses/{courseId}
def delete_pecv_bench_course(session: Session, course_short_name: str, max_retries: int = 3) -> bool:
    """Delete a course with retry logic using the given session and course ID.

    DELETE /core/admin/courses/{courseId}

    Sometimes it takes multiple scripts reruns to successfully delete a course.
    This approach fixes this.
    """

    logging.info(f"Attempting to delete course with shortName {course_short_name}")

    courseResponse: requests.Response = session.get(f"{SERVER_URL}/core/courses")
    courses = courseResponse.json()
    course_id = None
    for course in courses:
        if course["shortName"] == course_short_name:
            course_id = course["id"]
            break
    if course_id is None:
        logging.error(f"Course with shortName {course_short_name} not found")
        return False

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


# GET /core/courses
def get_pecv_bench_course_id(session: Session) -> int:
    """Get the course ID for the given course name using the provided session.

    GET /core/courses
    """
    course_short_name = parse_course_name_to_short_name()
    courseResponse: requests.Response = session.get(f"{SERVER_URL}/core/courses")
    courses = courseResponse.json()
    for course in courses:
        if course["shortName"] == course_short_name:
            logging.info(f"Found course ID {course['id']} for course shortName {course_short_name}")
            return course["id"]
    raise Exception(f"Course with shortName {course_short_name} not found")

#TODO
# GET /core/courses/{courseId}/exercises
def get_exercise_ids_from_pecv_bench(session: Session, course_id: int) -> int:
    """Get the exercise ID for the given exercise title in the specified course using the provided session."""
    """
    Retrieves programming exercises for a specific course and extracts IDs and Titles.

    :return: A list of dictionaries containing 'title' as key and 'id' as value.
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
        return transform_exercise_keys(exercises_map)

    except requests.exceptions.RequestException as e:
        print(f"Error fetching data: {e}")
        return {}
    except ValueError as e:
        print(f"Error parsing JSON: {e}")
        return {}


def transform_exercise_keys(input_dict):
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



