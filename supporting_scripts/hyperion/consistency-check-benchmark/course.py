import re
import json
import time
from typing import Dict
import requests
import urllib3
from logging_config import logging
from utils import COURSE_NAME, SPECIAL_CHARACTER_REGEX, SERVER_URL, IS_LOCAL_COURSE
from utils import login_as_admin

def __parse_course_name_to_short_name() -> str:
    """
    Parse course name to create a short name, removing special characters.

    Relies on COURSE_NAME and SPECIAL_CHARACTER_REGEX from utils.py and config.ini.

    :return: The parsed short name for the course.
    :rtype: str
    """
    logging.info(f"Parsing course name '{COURSE_NAME}' to generate short name")

    short_name = COURSE_NAME.strip()
    short_name = re.sub(SPECIAL_CHARACTER_REGEX, '', short_name.replace(' ', ''))

    if len(short_name) > 0 and not short_name[0].isalpha():
        short_name = 'a' + short_name

    logging.info(f"Generated short name '{short_name}' from course name '{COURSE_NAME}'")

    return short_name

def create_course_request(session: requests.Session) -> requests.Response:
    """
    Create a course using the given session.

    COURSE_NAME is specified in config.ini.

    POST /core/admin/courses

    Creates a course with COURSE_NAME from config.ini.

    :param requests.Session session: The active requests Session object.
    :return: The JSON response containing the created course details, such as ID etc.
    :rtype: requests.Response
    :raises Exception: If course creation fails or if the course cannot be deleted before recreation.
    """
    url = f"{SERVER_URL}/core/admin/courses"
    course_short_name = __parse_course_name_to_short_name()

    default_course = {
        "id": None,
        "title": COURSE_NAME,
        "shortName": course_short_name,
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

    # TODO better error handling for course creation: what if error 400 but it s not about existing course?
    if response.status_code == 400:
        logging.error(f"Course with shortName {course_short_name} already exists")

        # Try to delete existing course with retry logic
        course_is_deleted = delete_course_request(session)

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
        return response
    else:
        raise Exception(
                f"Could not create course {COURSE_NAME}; Status code: {response.status_code}\n"
                f"Double check whether the courseShortName {course_short_name} is valid (e.g. no special characters such as '-')!\n"
                f"Response content: {response.text}")

def delete_course_request(session: requests.Session,max_retries: int = 3) -> bool:
    """
    Delete a course with retry logic using the given session and course ID.

    COURSE_NAME is specified in config.ini.

    DELETE /core/admin/courses/{courseId}

    Sometimes it takes multiple scripts reruns to successfully delete a course.
    This approach fixes this.

    :param Session session: The active requests Session object.
    :param int max_retries: Maximum number of deletion attempts. Defaults to 3.
    :return: True if the course was successfully deleted, False otherwise.
    :rtype: bool
    """
    course_short_name = __parse_course_name_to_short_name()

    logging.info(f"Attempting to delete course with shortName {course_short_name}")

    course_id = get_course_id_request(session)

    delete_url = f"{SERVER_URL}/core/admin/courses/{course_id}"
    logging.info(f"Deleting course with ID {course_id} at URL {delete_url}")

    for attempt in range(1, max_retries +1):
        logging.info(f"Deletion attempt {attempt}/{max_retries}")
        try:
            # If server takes longer than 60s, it raises a Timeout exception, triggering the retry logic.
            logging.info("Sending DELETE request, it can take around 30 seconds to delete a course")
            delete_course_response: requests.Response = session.delete(delete_url, timeout = 60)
            if delete_course_response.status_code == 200:
                logging.info(f"DELETED: on attempt {attempt} course with shortName {course_short_name} was deleted successfully ")
                return True
            else:
                logging.error(f"Deletion attempt {attempt}/{max_retries} for {course_short_name} failed with status code {delete_course_response.status_code}")
        except Exception as e:
            logging.exception(f"Exception during deletion attempt {attempt}/{max_retries} for {course_short_name}: {e}")

        if attempt < max_retries:
            wait_time = 2 * attempt
            logging.info(f"Waiting {wait_time} seconds before retrying deletion...")
            time.sleep(wait_time)
    logging.error(f"Failed to delete course with shortName {course_short_name} after {max_retries} attempts.")
    return False

def get_course_id_request(session: requests.Session) -> int:
    """
    Get the course ID for the given course name using the provided session.

    COURSE_NAME is specified in config.ini.

    GET /core/courses

    :param Session session: The active requests Session object.
    :return: The ID of the course.
    :rtype: int
    :raises Exception: If the course with the generated short name is not found.
    """
    logging.info("Retrieving PECV-Bench course ID")
    course_short_name = __parse_course_name_to_short_name()
    courseResponse: requests.Response = session.get(f"{SERVER_URL}/core/courses")
    courses = courseResponse.json()
    for course in courses:
        if course["shortName"] == course_short_name:
            logging.info(f"Found course ID {course['id']} for course shortName {course_short_name}")
            return course["id"]
    raise Exception(f"Course with shortName {course_short_name} not found")

def get_exercise_ids_request(session: requests.Session, course_id: int) -> Dict[str, int]:
    """
    Retrieves programming exercises for a specific course and extracts IDs and Titles.

    GET /programming/courses/{courseId}/programming-exercises

    Need to call get_course_id_request first to get course_id, which searches for COURSE_NAME ID.

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
            ex_title = exercise.get("title")
            ex_id = exercise.get("id")

            if ex_title is not None and ex_id is not None:
                exercises_map[ex_title] = ex_id

        return __transform_exercise_json_keys(exercises_map)

    except requests.exceptions.RequestException as e:
        logging.error(f"Error fetching data: {e}")
        return {}
    except ValueError as e:
        logging.error(f"Error parsing JSON: {e}")
        return {}

def __transform_exercise_json_keys(input_dict: Dict[str, int]) -> Dict[str, int]:
    """
    Transforms the keys of the input dictionary into a new format.

    <'007 - H00 - Hello World ASM': 1736 -> H00-Hello_World_ASM:007': 1736> to easily map it with COURSE_EXERCISES from config.ini
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
            logging.warning(f"Could not parse key '{original_key}'")
            transformed_dict[original_key] = value

    return transformed_dict

if __name__ == "__main__":
    logging.info("Creating Hyperion Benchmark Course")

    logging.info("Step 1: Creating session")
    session = requests.Session()

    logging.info("Step 2: Logging in as admin")
    login_as_admin(session=session)

    #logging.info("Step 3: Creating Hyperion Benchmark Course")
    #create_course_request(session=session)

    logging.info("Step 4: Retrieving Hyperion Benchmark Course ID")
    course_id = get_course_id_request(session=session)

    logging.info("Step 5: Retrieving programming exercise IDs for the course")
    exercise_ids = get_exercise_ids_request(session=session, course_id=course_id)
    print(exercise_ids)