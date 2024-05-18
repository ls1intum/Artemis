import requests
import configparser
import json
import urllib3
import re
import logging

from utils import login_as_admin
from add_users_to_course import add_users_to_groups_of_course

config = configparser.ConfigParser()
config.read('config.ini')

server_url = config.get('Settings', 'server_url')
is_local_course = config.get('CourseSettings', 'is_local_course')
is_local_course = is_local_course.lower() == 'true'  # convert to boolean
course_name = config.get('CourseSettings', 'course_name')

special_characters_reg_ex = r'[^a-zA-Z0-9_]'


def parse_course_name_to_short_name(course_name):
    short_name = course_name.strip()
    short_name = short_name.replace(' ', '')
    short_name = re.sub(special_characters_reg_ex, '', short_name)

    short_name_does_not_start_with_letter = len(short_name) > 0 and not short_name[0].isalpha()
    if short_name_does_not_start_with_letter:
        short_name = 'a' + short_name

    return short_name


def create_course(session):
    url = f"{server_url}/api/admin/courses"

    course_short_name = parse_course_name_to_short_name(course_name)
    default_course = {
        "id": None,
        "title": str(course_name),
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

    if is_local_course:
        default_course["customizeGroupNames"] = True
        # If it's a local course, use the original group names without the prefix
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
        logging.info(f"Created course {course_name} with shortName {course_short_name} \n {response.json()}")
    else:
        logging.error("Problem with the group 'students' and interacting with a test server? Is 'is_local_course' in "
                      "'config.ini' set to 'False'?")
        raise Exception(
            f"Could not create course {course_name}; Status code: {response.status_code}\n Double check whether the courseShortName {course_short_name} is not already used for another course!\nResponse content: {response.text}")
    return response


def main():
    session = requests.session()
    login_as_admin(session)
    created_course_response = create_course(session)

    response_data = created_course_response.json()  # This will parse the response content as JSON
    course_id = response_data["id"]

    add_users_to_groups_of_course(session, course_id)


if __name__ == "__main__":
    main()
