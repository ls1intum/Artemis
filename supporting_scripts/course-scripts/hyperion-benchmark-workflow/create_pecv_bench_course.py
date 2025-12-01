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

def parse_course_name_to_short_name() -> str:
    """Parse course name to create a short name, removing special characters."""
    short_name = COURSE_NAME.strip()
    short_name = re.sub(SPECIAL_CHARACTERS_REGEX, '', short_name.replace(' ', ''))

    if len(short_name) > 0 and not short_name[0].isalpha():
        short_name = 'a' + short_name

    return short_name

def create_pecv_bench_course(session: Session) -> requests.Response:
    """Create a course using the given session."""
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
    

def delete_pecv_bench_course(session: Session, course_short_name: str, max_retries: int = 3) -> bool:
    """Delete a course with retry logic using the given session and course ID.
    
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
    