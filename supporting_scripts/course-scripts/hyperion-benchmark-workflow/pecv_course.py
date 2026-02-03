import re
import sys
import os
import configparser
import json
import time
import requests
import urllib3
from concurrent.futures import ThreadPoolExecutor, as_completed
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

def create_pecv_bench_course_request(session: requests.Session) -> requests.Response:
    """
    Create a course using the given session.

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

def delete_pecv_bench_course_request(session: requests.Session, course_short_name: str, max_retries: int = 3) -> bool:
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

def get_pecv_bench_course_id_request(session: requests.Session) -> int:
    """
    Get the course ID for the given course name using the provided session.

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

if __name__ == "__main__":
    logging.info("Creating PECV-Bench Hyperion Benchmark Course")

    logging.info("Step 1: Creating session")
    session = requests.Session()

    logging.info("Step 2: Logging in as admin")
    login_as_admin(session=session)

    logging.info("Step 3: Creating PECV-Bench course")
    create_pecv_bench_course_request(session=session)