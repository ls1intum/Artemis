import sys
import requests
import configparser
import json
import urllib3
import re
import uuid
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

    response: requests.Response = session.post(url, data=body, headers=headers)
    
    if response.status_code == 201:
        logging.info(f"Created course {COURSE_NAME} with shortName {course_short_name} \n {response.json()}")
    elif response.status_code == 400:
        logging.info(f"Course with shortName {course_short_name} already exists.")
        
        course_is_deleted = delete_pecv_bench_course(session, course_short_name)
        
        if course_is_deleted:
            logging.info(f"Retrying course creation for {COURSE_NAME} after deletion.")
            
            response: requests.Response = session.post(url, data=body, headers=headers)
            
            if response.status_code == 201:
                logging.info(f"Created course {COURSE_NAME} with shortName {course_short_name}. \n {response.json()}")
            else:
                logging.error(f"Failed to create course {COURSE_NAME} after deletion. Status code: {response.status_code}\n Response content: {response.text}")
        sys.exit(0)
    else:
        logging.error("Problem with the group 'students' and interacting with a test server? "
                        "Is 'is_local_course' in 'config.ini' set to 'False'?")
        raise Exception(
            f"Could not create course {COURSE_NAME}; Status code: {response.status_code}\n"
            f"Double check whether the courseShortName {course_short_name} is valid (e.g. no special characters such as '-')!\n"
            f"Response content: {response.text}")
    
    return response.json()

def delete_pecv_bench_course(session: Session, course_short_name: str) -> bool:
    """Delete a course using the given session and course ID."""

    coursesResponse: requests.Response = session.get(f"{SERVER_URL}/core/courses")

    courses = coursesResponse.json()
    course_id = None
    for course in courses:
        if course["shortName"] == course_short_name:
            course_id = course["id"]
            break
    
    deleteCourseResponse: requests.Response = session.delete(f"{SERVER_URL}/core/admin/courses/{course_id}")
    if deleteCourseResponse.status_code == 200:
        logging.info(f"Deleted course with shortName {course_short_name}")
        return True
    else:
        logging.error(f"Could not delete course with shortName {course_short_name}")
        return False
    
    #nextResponse: requests.Response = session.get(f"{SERVER_URL}/programming/courses/{course_id}/programming-exercises")
    #exercises = nextResponse.json()
    #for exercise in exercises:
    #    logging.info(f"Existing exercise in the course: {exercise['title']} (ID: {exercise['id']})")