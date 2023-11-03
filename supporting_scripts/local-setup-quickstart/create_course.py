import requests
import configparser
import json
import urllib3

from utils import login_as_admin
from utils import print_success

config = configparser.ConfigParser()
config.read('config.ini')

server_url = config.get('Settings', 'server_url')


def create_course(session, course_name, course_short_name, is_local_course):
    url = f"{server_url}/api/admin/courses"

    default_course = {
        "id": None,
        "title": str(course_name),
        "shortName": str(course_short_name),
        "customizeGroupNames": True,
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
        print_success(f"Created course {course_name} with id {course_short_name}")
    else:
        raise Exception(
            f"Could not create course {course_name}; Status code: {response.status_code}\n Double check whether the courseShortName {course_short_name} is not already used for another course!\nResponse content: {response.text}")




def main():
    session = requests.session()

    course_name = 'Local Course'
    course_short_name = "localCourse"
    is_local_course = True

    login_as_admin(session)
    create_course(session, course_name, course_short_name, is_local_course)


if __name__ == "__main__":
    main()
