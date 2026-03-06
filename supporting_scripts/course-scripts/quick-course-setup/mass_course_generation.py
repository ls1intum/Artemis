"""
Mass Course Generation Script

This script generates a large number of courses (e.g., 2000) with a specified number
of students per course (e.g., 1500), where each course has unique user groups.

The same pool of students is reused across all courses - only the user groups differ.

WARNING: This script creates a massive amount of data. Only run on a local instance!
"""

import requests
import configparser
import json
import urllib3
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import Dict, Any, List
from requests import Session

from logging_config import logging
from utils import authenticate_user

# Load configuration
config = configparser.ConfigParser()
config.read(['../config.ini', 'config.ini', 'mass_generation_config.ini'])

# Constants from config file
SERVER_URL: str = config.get('Settings', 'server_url')
CLIENT_URL: str = config.get('Settings', 'client_url')
ADMIN_USER: str = config.get('Settings', 'admin_user')
ADMIN_PASSWORD: str = config.get('Settings', 'admin_password')

try:
    MAX_WORKERS: int = int(config.get("Settings", "max_threads"))
except (configparser.NoOptionError, configparser.NoSectionError, ValueError):
    MAX_WORKERS = 8

# Mass generation settings - with fallback to defaults
try:
    COURSES_TO_CREATE: int = int(config.get("MassGeneration", "courses"))
    STUDENTS_TO_CREATE: int = int(config.get("MassGeneration", "students_per_course"))
    COURSE_NAME_PREFIX: str = config.get("MassGeneration", "course_name_prefix")
    COURSE_SHORT_NAME_PREFIX: str = config.get("MassGeneration", "course_short_name_prefix")
except (configparser.NoOptionError, configparser.NoSectionError):
    # Fallback defaults
    COURSES_TO_CREATE = 2000
    STUDENTS_TO_CREATE = 1500
    COURSE_NAME_PREFIX = "MassGenCourse"
    COURSE_SHORT_NAME_PREFIX = "mgc"


def create_course_with_custom_groups(
    session: Session,
    course_number: int,
    course_short_name: str,
    student_group: str,
    tutor_group: str,
    editor_group: str,
    instructor_group: str
) -> Dict[str, Any]:
    """Create a course with custom group names."""
    url = f"{SERVER_URL}/core/admin/courses"
    course_title = f"{COURSE_NAME_PREFIX}-{course_number}"

    course_data = {
        "id": None,
        "title": course_title,
        "shortName": course_short_name,
        "customizeGroupNames": True,
        "studentGroupName": student_group,
        "teachingAssistantGroupName": tutor_group,
        "editorGroupName": editor_group,
        "instructorGroupName": instructor_group,
        "courseInformationSharingMessagingCodeOfConduct": None,
        "semester": None,
        "testCourse": True,  # Mark as test course
        "onlineCourse": False,
        "complaintsEnabled": False,  # Disable to reduce overhead
        "requestMoreFeedbackEnabled": False,
        "maxPoints": None,
        "accuracyOfScores": 1,
        "defaultProgrammingLanguage": None,
        "maxComplaints": 0,
        "maxTeamComplaints": 0,
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
        "course": ('blob.json', json.dumps(course_data), 'application/json')
    }

    body, content_type = urllib3.filepost.encode_multipart_formdata(fields)
    headers = {'Content-Type': content_type}

    response = session.post(url, data=body, headers=headers)

    if response.status_code == 201:
        logging.info(f"Created course {course_title} with shortName {course_short_name}")
        return response.json()
    elif response.status_code == 400:
        logging.warning(f"Course {course_short_name} already exists, skipping...")
        return None
    else:
        raise Exception(
            f"Could not create course {course_title}; Status code: {response.status_code}\n"
            f"Response content: {response.text}"
        )


def create_student_user(session: Session, student_number: int) -> str:
    """Create a single student user (shared across all courses)."""
    username = f"mass_student{student_number}"

    user_details = {
        "activated": True,
        "authorities": ["ROLE_USER"],
        "login": username,
        "email": f"{username}@example.com",
        "firstName": "Student",
        "lastName": f"Number{student_number}",
        "langKey": "en",
        "groups": [],  # Will be added to course-specific groups later
        "password": username  # Password is same as username
    }

    url = f"{CLIENT_URL}/core/admin/users"
    headers = {"Content-Type": "application/json"}
    response = session.post(url, json=user_details, headers=headers)

    if response.status_code == 201:
        logging.info(f"Created user {username}")
        return username
    elif response.status_code == 400 and "userExists" in response.json().get("errorKey", ""):
        logging.info(f"User {username} already exists.")
        return username
    else:
        raise Exception(
            f"Creating {username} failed. Status code: {response.status_code}\n"
            f"Response content: {response.text}"
        )


def create_all_students(session: Session) -> List[str]:
    """Create all students that will be reused across courses."""
    logging.info(f"Creating {STUDENTS_TO_CREATE} students...")
    students = []

    with ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
        futures = []
        for student_num in range(1, STUDENTS_TO_CREATE + 1):
            future = executor.submit(create_student_user, session, student_num)
            futures.append(future)

        for future in as_completed(futures):
            try:
                username = future.result()
                students.append(username)
            except Exception as e:
                logging.exception(f"Error creating student: {e}")

    logging.info(f"Successfully created/verified {len(students)} students")
    return students


def add_student_to_course(
    session: Session,
    course_id: str,
    student_group: str,
    username: str
) -> None:
    """Add a student to a course."""
    url = f"{SERVER_URL}/core/courses/{course_id}/{student_group}/{username}"
    response = session.post(url)

    if response.status_code == 200:
        logging.info(f"Added user {username} to course {course_id}")
    else:
        logging.error(f"Could not add user {username} to course {course_id}")


def process_course(admin_session: Session, course_number: int, student_usernames: List[str]) -> None:
    """
    Process a single course: create it and add existing students to it.
    Each course gets unique user groups, but uses the same pool of students.
    """
    try:
        # Generate unique group names for this course
        course_short_name = f"{COURSE_SHORT_NAME_PREFIX}{course_number}"
        student_group = f"course{course_number}-students"
        tutor_group = f"course{course_number}-tutors"
        editor_group = f"course{course_number}-editors"
        instructor_group = f"course{course_number}-instructors"

        # Create the course
        course_data = create_course_with_custom_groups(
            admin_session,
            course_number,
            course_short_name,
            student_group,
            tutor_group,
            editor_group,
            instructor_group
        )

        if course_data is None:
            logging.warning(f"Course {course_number} already exists, skipping")
            return

        course_id = course_data["id"]
        logging.info(f"Course {course_number} created with ID {course_id}")

        # Add all students to this course with the course-specific student group
        # Process in batches to avoid overwhelming the system
        student_batch_size = 100

        for batch_start in range(0, len(student_usernames), student_batch_size):
            batch_end = min(batch_start + student_batch_size, len(student_usernames))
            batch = student_usernames[batch_start:batch_end]

            with ThreadPoolExecutor(max_workers=min(MAX_WORKERS, 4)) as executor:
                futures = []
                for username in batch:
                    future = executor.submit(
                        add_student_to_course,
                        admin_session,
                        course_id,
                        student_group,
                        username
                    )
                    futures.append(future)

                # Wait for all additions to complete
                for future in as_completed(futures):
                    try:
                        future.result()
                    except Exception as e:
                        logging.exception(f"Error adding student to course {course_number}: {e}")

            logging.info(
                f"Course {course_number}: Added students {batch_start + 1} to {batch_end}"
            )

        logging.info(
            f"Completed course {course_number}/{COURSES_TO_CREATE} with {len(student_usernames)} students"
        )

    except Exception as e:
        logging.exception(f"Error processing course {course_number}: {e}")


def main() -> None:
    """
    Main function to generate multiple courses with students.

    This script creates:
    1. A pool of N students (reused across all courses)
    2. M courses, each with unique user groups
    3. All students are registered to each course with course-specific groups

    WARNING: This creates a massive amount of data!
    Only run on a local test instance!
    """
    logging.info("=" * 80)
    logging.info("MASS COURSE GENERATION SCRIPT")
    logging.info("=" * 80)
    logging.info(f"Students to create (shared pool): {STUDENTS_TO_CREATE}")
    logging.info(f"Courses to create: {COURSES_TO_CREATE}")
    logging.info(f"Students per course: {STUDENTS_TO_CREATE}")
    logging.info(f"Total course registrations: {COURSES_TO_CREATE * STUDENTS_TO_CREATE}")
    logging.info("=" * 80)
    logging.info("WARNING: This will create a MASSIVE amount of data!")
    logging.info("Only proceed if you are running on a local test instance.")
    logging.info("=" * 80)

    # Authenticate as admin
    admin_session = requests.Session()
    authenticate_user(ADMIN_USER, ADMIN_PASSWORD, admin_session)
    logging.info("Admin authentication successful")

    # Step 1: Create all students (shared pool)
    logging.info("Step 1: Creating student pool...")
    student_usernames = create_all_students(admin_session)

    if len(student_usernames) == 0:
        logging.error("No students were created. Aborting.")
        return

    logging.info(f"Student pool ready: {len(student_usernames)} students")

    # Step 2: Create courses and add students
    logging.info("Step 2: Creating courses and registering students...")
    for course_num in range(1, COURSES_TO_CREATE + 1):
        process_course(admin_session, course_num, student_usernames)

        # Progress update every 10 courses
        if course_num % 10 == 0:
            logging.info(f"Progress: {course_num}/{COURSES_TO_CREATE} courses completed")

    logging.info("=" * 80)
    logging.info("MASS COURSE GENERATION COMPLETE")
    logging.info("=" * 80)
    logging.info(f"Created {len(student_usernames)} students")
    logging.info(f"Created {COURSES_TO_CREATE} courses")
    logging.info(f"Total registrations: {COURSES_TO_CREATE * len(student_usernames)}")
    logging.info("=" * 80)


if __name__ == "__main__":
    main()
