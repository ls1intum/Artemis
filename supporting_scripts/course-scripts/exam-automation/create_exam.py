import requests
import configparser
import re
import time
from datetime import datetime, timedelta, timezone
from typing import Dict, Any, List
from logging_config import logging
from utils import authenticate_user

# Load configuration
config = configparser.ConfigParser()
config.read(['../config.ini', 'config.ini'])


# Constants from config
try:
    SERVER_URL: str = config.get('Settings', 'server_url')
    ADMIN_USER: str = config.get('Settings', 'admin_user')
    ADMIN_PASSWORD: str = config.get('Settings', 'admin_password')
    COURSE_ID: int = int(config.get('ExamSettings', 'course_id'))
    EXAM_TITLE: str = config.get('ExamSettings', 'exam_title')
    PROGRAMMING_EXERCISE_NAME: str = config.get('ExamSettings', 'programming_exercise_name')
    NUMBER_OF_CORRECTION_ROUNDS: int = int(config.get('ExamSettings', 'number_of_correction_rounds'))
except (configparser.NoSectionError, configparser.NoOptionError, ValueError) as e:
    logging.error(f"Configuration error: {str(e)}")
    logging.error("Please check your config.ini file has all required settings.")
    exit(1)

def generate_exam_short_name(title: str) -> str:
    """Generate a short name for the exam based on the title."""
    # Remove special characters and convert to uppercase
    short_name = re.sub(r'[^a-zA-Z0-9\s]', '', title)
    short_name = re.sub(r'\s+', '', short_name)
    return short_name.upper()

def calculate_exam_dates() -> tuple[str, str, str, int, str, str]:
    """Calculate exam start, end, and visible dates based on current time."""
    now = datetime.now(timezone.utc)
    start_date = (now + timedelta(hours=1)).strftime("%Y-%m-%dT%H:%M:%S+00:00")
    end_date = (now + timedelta(hours=2)).strftime("%Y-%m-%dT%H:%M:%S+00:00")
    visible_date = (now + timedelta(minutes=59)).strftime("%Y-%m-%dT%H:%M:%S+00:00")
    working_time = int((datetime.fromisoformat(end_date[:-6]) - datetime.fromisoformat(start_date[:-6])).total_seconds())
    review_start_date = (datetime.fromisoformat(end_date[:-6]) + timedelta(hours=5)).strftime("%Y-%m-%dT%H:%M:%S+00:00")
    review_end_date = (datetime.fromisoformat(end_date[:-6]) + timedelta(days=1)).strftime("%Y-%m-%dT%H:%M:%S+00:00")
    return start_date, end_date, visible_date, working_time, review_start_date, review_end_date

def create_exam(session: requests.Session, course_id: int, title: str, short_name: str) -> Dict[str, Any]:
    """Create an exam in the specified course."""
    url: str = f"{SERVER_URL}/exam/courses/{course_id}/exams"

    start_date, end_date, visible_date, working_time, review_start_date, review_end_date = calculate_exam_dates()

    payload: Dict[str, Any] = {
        "title": title,
        "shortName": short_name,
        "startDate": start_date,
        "endDate": end_date,
        "workingTime": working_time,
        "visibleDate": visible_date,
        "startText": "Good luck with your exam!",
        "endText": "Thank you for participating in the exam.",
        "confirmationStartText": "Are you sure you want to start the exam?",
        "confirmationEndText": "Are you sure you want to end the exam?",
        "maxPoints": 10,
        "examMaxPoints": 10,
        "gracePeriod": 180,
        "numberOfCorrectionRoundsInExam": NUMBER_OF_CORRECTION_ROUNDS,
        "randomizeExerciseOrder": False,
        "testExam": False,
        "numberOfExercisesInExam": 1,
        "examStudentReviewStart": review_start_date,
        "examStudentReviewEnd": review_end_date,
        "course": {"id": course_id}
    }

    response: requests.Response = session.post(url, json=payload)

    if response.status_code == 201:
        exam_data = response.json()
        logging.info(f"Created exam '{title}' with ID {exam_data.get('id')}")
        return exam_data
    elif response.status_code == 404:
        logging.error(f"Course with ID {course_id} does not exist.")
        logging.error("Please check your course_id in config.ini or create a course first using the quick-course-setup scripts.")
        raise Exception(f"Course {course_id} not found")
    else:
        raise Exception(f"Failed to create exam. Status code: {response.status_code}\nResponse: {response.text}")

def get_course_students(session: requests.Session, course_id: int) -> List[Dict[str, Any]]:
    """Get all students enrolled in the course."""
    url: str = f"{SERVER_URL}/core/courses/{course_id}/students"
    response: requests.Response = session.get(url)

    if response.status_code == 200:
        students = response.json()
        logging.info(f"Found {len(students)} students in course {course_id}")
        return students
    else:
        raise Exception(f"Failed to get course students. Status code: {response.status_code}\nResponse: {response.text}")

def register_students_for_exam(session: requests.Session, exam_id: int) -> None:
    """Register students for the exam."""
    url: str = f"{SERVER_URL}/exam/courses/{COURSE_ID}/exams/{exam_id}/register-course-students"

    response: requests.Response = session.post(url)

    if response.status_code == 200:
        logging.info(f"Successfully registered all course students for exam {exam_id}")
    else:
        raise Exception(f"Failed to register students for exam. Status code: {response.status_code}\nResponse: {response.text}")

def create_exercise_group(session: requests.Session, exam_id: int, title: str) -> Dict[str, Any]:
    """Create an exercise group in the exam."""
    url: str = f"{SERVER_URL}/exam/courses/{COURSE_ID}/exams/{exam_id}/exercise-groups"

    payload: Dict[str, Any] = {
        "title": title,
        "isMandatory": True,
        "exam": {"id": exam_id}
    }

    response: requests.Response = session.post(url, json=payload)

    if response.status_code == 201:
        exercise_group_data = response.json()
        logging.info(f"Created exercise group '{title}' in exam {exam_id}")
        return exercise_group_data
    else:
        raise Exception(f"Failed to create exercise group. Status code: {response.status_code}\nResponse: {response.text}")

def create_programming_exercise_in_exam(session: requests.Session, exercise_group_id: int, exercise_name: str, exercise_number: int = 1) -> Dict[str, Any]:
    """Create a default programming exercise in the exercise group with minimal required fields."""
    url: str = f"{SERVER_URL}/programming/programming-exercises/setup"

    payload: Dict[str, Any] = {
        "type": "programming",
        "title": f"{exercise_name} {exercise_number} {int(time.time())}",
        "shortName": f"ExamExercise{exercise_number}{int(time.time())}",
        "maxPoints": 10,
        "assessmentType": "SEMI_AUTOMATIC",
        "programmingLanguage": "JAVA",
        "exerciseGroup": {"id": exercise_group_id},
        "allowOnlineEditor": True,
        "allowOfflineIde": True,
        "allowComplaintsForAutomaticAssessments": True,
        "buildConfig": {
            "buildScript": "#!/usr/bin/env bash\nset -e\n\ngradle () {\n  echo '⚙️ executing gradle'\n  chmod +x ./gradlew\n  ./gradlew clean test\n}\n\nmain () {\n  gradle\n}\n\nmain \"${@}\"\n",
            "checkoutSolutionRepository": False
        },
        "packageName": "de.tum.in.www1.example",
        "projectType": "PLAIN_GRADLE",
        "staticCodeAnalysisEnabled": False,
    }

    response: requests.Response = session.post(url, json=payload)

    if response.status_code == 201:
        exercise_data = response.json()
        logging.info(f"Created default programming exercise '{payload['title']}' in exercise group {exercise_group_id}")
        # Combine our payload with the response to get complete exercise data
        # This ensures we have all required fields for building the exercise template
        complete_exercise = {**payload, **exercise_data}

        # Log key fields for debugging
        logging.info(f"Exercise ID: {complete_exercise.get('id')}")
        logging.info(f"Project Key: {complete_exercise.get('projectKey')}")
        logging.info(f"Template Repository: {complete_exercise.get('templateParticipation', {}).get('repositoryUri')}")
        logging.info(f"Solution Repository: {complete_exercise.get('solutionParticipation', {}).get('repositoryUri')}")
        logging.info(f"Test Repository: {complete_exercise.get('testRepositoryUri')}")

        return complete_exercise
    else:
        raise Exception(f"Failed to create programming exercise. Status code: {response.status_code}\nResponse: {response.text}")

def main() -> int:
    """Main function to create exam and register students."""
    try:
        # Step 1: Authenticate as admin
        session: requests.Session = requests.Session()
        authenticate_user(ADMIN_USER, ADMIN_PASSWORD, session)

        if COURSE_ID is None:
            logging.error("Course ID is required. Please provide course_id in config.ini or pass it as parameter.")
            return None

        logging.info(f"Using course ID: {COURSE_ID}")

        exam_short_name = generate_exam_short_name(EXAM_TITLE)

        # Step 2: Create the exam
        exam_data = create_exam(session, COURSE_ID, EXAM_TITLE, exam_short_name)
        exam_id = exam_data.get('id')

        # Step 3: Get course students
        students = get_course_students(session, COURSE_ID)

        # Step 4: Register students for the exam
        register_students_for_exam(session, exam_id)

        # Step 5: Create exercise group and programming exercise
        # Create exercise group first
        exercise_group = create_exercise_group(session, exam_id, "Programming Exercises")
        exercise_group_id = exercise_group.get('id')

        # Create programming exercise in the exercise group
        create_programming_exercise_in_exam(session, exercise_group_id, PROGRAMMING_EXERCISE_NAME)

        logging.info(f"Exam setup completed successfully!")
        logging.info(f"Course ID: {COURSE_ID}")
        logging.info(f"Exam ID: {exam_id}")
        logging.info(f"Exam Title: {EXAM_TITLE}")
        logging.info(f"Exam Short Name: {exam_short_name}")
        logging.info(f"Students Registered: {len(students)}")

        return exam_id

    except Exception as e:
        if "Course" in str(e) and "not found" in str(e):
            logging.error("❌ Course not found!")
            logging.error("Please run the quick-course-setup scripts first to create a course.")
            logging.error("Then update the course_id in config.ini to match the created course.")
        else:
            logging.error(f"Error during exam creation: {str(e)}")
        exit(1)

if __name__ == "__main__":
    main()
