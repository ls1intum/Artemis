import requests
import configparser
import json
import urllib3

from utils import login_as_admin
from utils import print_error
from utils import print_success

config = configparser.ConfigParser()
config.read('config.ini')

backend_url = config.get('Settings', 'backend_url')


def create_course(session, course_name, course_short_name):
    url = f"{backend_url}/api/admin/courses"

    default_course = {
        "id": None,
        "title": str(course_name),
        "shortName": str(course_short_name),
        "customizeGroupNames": True,
        "studentGroupName": "students",
        "teachingAssistantGroupName": "tutors",
        "editorGroupName": "editors",
        "instructorGroupName": "instructors",
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
        print_error(f"Could not create course {course_name}; Status code: {response.status_code}")
        print_error("Double check whether the courseId is not already used for another course!")


def main():
    session = requests.session()

    course_name = 'Local Course'
    course_short_name = "localCourse"

    login_as_admin(session)
    create_course(session, course_name, course_short_name)


if __name__ == "__main__":
    main()
