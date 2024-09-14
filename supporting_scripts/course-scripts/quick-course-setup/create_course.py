import sys
import requests
import configparser
import json
import urllib3
import re
from logging_config import logging
from requests import Session
from utils import login_as_admin
from add_users_to_course import add_users_to_groups_of_course
from randomize_results_after import run_cleanup

# Load configuration
config = configparser.ConfigParser()
config.read('config.ini')

# Constants from config file
SERVER_URL: str = config.get('Settings', 'server_url')
IS_LOCAL_COURSE: bool = config.get('CourseSettings', 'is_local_course').lower() == 'true'  # Convert to boolean
COURSE_NAME: str = config.get('CourseSettings', 'course_name')
SPECIAL_CHARACTERS_REGEX: str = config.get('Settings', 'special_character_regex')

def parse_course_name_to_short_name() -> str:
    """Parse course name to create a short name, removing special characters."""
    short_name = COURSE_NAME.strip()
    short_name = re.sub(SPECIAL_CHARACTERS_REGEX, '', short_name.replace(' ', ''))

    if len(short_name) > 0 and not short_name[0].isalpha():
        short_name = 'a' + short_name

    return short_name

def create_course(session: Session) -> requests.Response:
    """Create a course using the given session."""
    url = f"{SERVER_URL}/admin/courses"
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

    response = session.post(url, data=body, headers=headers)

    if response.status_code == 201:
        logging.info(f"Created course {COURSE_NAME} with shortName {course_short_name} \n {response.json()}")
    elif response.status_code == 400:
        logging.info(f"Course with shortName {course_short_name} already exists. Please provide the course ID in the config file and set create_course to FALSE if you intend to add programming exercises to this course.")
        run_cleanup()
        sys.exit(0)
    else:
        logging.error("Problem with the group 'students' and interacting with a test server? "
                      "Is 'is_local_course' in 'config.ini' set to 'False'?")
        raise Exception(
            f"Could not create course {COURSE_NAME}; Status code: {response.status_code}\n"
            f"Double check whether the courseShortName {course_short_name} is valid (e.g. no special characters such as '-')!\n"
            f"Response content: {response.text}")

    return response.json()

def main() -> None:
    """Main function to create a course and add users."""
    session = requests.session()
    login_as_admin(session)

    response_data = create_course(session)
    course_id = response_data["id"]

    add_users_to_groups_of_course(session, course_id)

if __name__ == "__main__":
    main()
