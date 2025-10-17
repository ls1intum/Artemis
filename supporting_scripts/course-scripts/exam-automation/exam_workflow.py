import requests
import configparser
import time
import threading
from datetime import datetime, timedelta, timezone
from typing import Dict, Any, List
from logging_config import logging
from create_exam import main as create_exam_main
from utils import authenticate_user

# Load configuration
config = configparser.ConfigParser()
config.read(['../config.ini', 'config.ini'])

# Constants from config
try:
    SERVER_URL: str = config.get('Settings', 'server_url')
    CLIENT_URL: str = config.get('Settings', 'client_url')
    ADMIN_USER: str = config.get('Settings', 'admin_user')
    ADMIN_PASSWORD: str = config.get('Settings', 'admin_password')
    COURSE_ID: int = int(config.get('ExamSettings', 'course_id'))
except (configparser.NoSectionError, configparser.NoOptionError, ValueError) as e:
    logging.error(f"Configuration error: {str(e)}")
    logging.error("Please check your config.ini file has all required settings.")
    exit(1)

def get_exam_exercises(session: requests.Session, exam_id: int) -> List[Dict[str, Any]]:
    """Get all exercises in the exam."""
    url = f"{SERVER_URL}/exam/courses/{COURSE_ID}/exams/{exam_id}/exercise-groups"
    response = session.get(url)

    if response.status_code != 200:
        logging.error(f"Failed to get exam exercises. Status code: {response.status_code}")
        return []

    exercise_groups = response.json()
    exercises = []
    for group in exercise_groups:
        if 'exercises' in group:
            exercises.extend(group['exercises'])
    logging.info(f"Found {len(exercises)} exercises in exam {exam_id}")
    return exercises

def get_course_students(session: requests.Session) -> List[Dict[str, Any]]:
    """Get all students enrolled in the course."""
    url = f"{SERVER_URL}/core/courses/{COURSE_ID}/students"
    response = session.get(url)

    if response.status_code != 200:
        raise Exception(f"Failed to get course students. Status code: {response.status_code}")

    students = response.json()
    logging.info(f"Found {len(students)} students in course {COURSE_ID}")
    return students

def student_submit_exercise(student_username: str, student_password: str, exercise_id: int, exam_id: int) -> None:
    """Have a student submit an exercise and then submit the exam."""
    try:
        # Authenticate as student
        student_session = requests.Session()
        authenticate_user(student_username, student_password, student_session)

        # Get participation
        participation_url = f"{CLIENT_URL}/exercise/exercises/{exercise_id}/participations"
        participation_response = student_session.post(participation_url)

        if participation_response.status_code != 201:
            logging.error(f"Failed to create participation for student {student_username}. Status: {participation_response.status_code}")
            return

        participation = participation_response.json()
        participation_id = participation.get('id')

        # Make a commit
        commit_url = f"{CLIENT_URL}/programming/repository/{participation_id}/commit"
        commit_response = student_session.post(commit_url)

        if commit_response.status_code not in [200, 201]:
            logging.error(f"Failed to commit for student {student_username}. Status: {commit_response.status_code}")
            return

        logging.info(f"Student {student_username} successfully committed exercise {exercise_id}")

        # Now submit the exam
        # First get the student exam
        student_exam_url = f"{SERVER_URL}/exam/courses/{COURSE_ID}/exams/{exam_id}/own-student-exam"
        student_exam_response = student_session.get(student_exam_url)

        if student_exam_response.status_code != 200:
            logging.error(f"Failed to get student exam for {student_username}. Status: {student_exam_response.status_code}\nResponse: {student_exam_response.text}")
            return

        student_exam = student_exam_response.json()
        student_exam_id = student_exam.get('id')

        # Get the conduction data
        conduction_url = f"{SERVER_URL}/exam/courses/{COURSE_ID}/exams/{exam_id}/student-exams/{student_exam_id}/conduction"
        conduction_response = student_session.get(conduction_url)

        if conduction_response.status_code != 200:
            logging.error(f"Failed to get conduction data for {student_username}. Status: {conduction_response.status_code}\nResponse: {conduction_response.text}")
            return

        student_exam_conduction = conduction_response.json()

        # Submit the exam
        submit_url = f"{SERVER_URL}/exam/courses/{COURSE_ID}/exams/{exam_id}/student-exams/submit"
        submit_response = student_session.post(submit_url, json=student_exam_conduction)

        if submit_response.status_code != 200:
            logging.error(f"Failed to submit exam for student {student_username}. Status: {submit_response.status_code}\nResponse: {submit_response.text}")
            return

        logging.info(f"Student {student_username} successfully submitted exam")

    except Exception as e:
        logging.error(f"Error with student {student_username}: {str(e)}")

def end_exam(session: requests.Session, exam_id: int) -> None:
    """End the exam by setting the end date to current time and publishing results."""
    current_time = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S+00:00")

    # First get the current exam to update it
    url = f"{SERVER_URL}/exam/courses/{COURSE_ID}/exams/{exam_id}"
    response = session.get(url)

    if response.status_code != 200:
        logging.error(f"Failed to get exam data. Status code: {response.status_code}\nResponse: {response.text}")
        return

    exam_data = response.json()
    exam_data["endDate"] = current_time
    exam_data["publishResultsDate"] = (datetime.fromisoformat(current_time[:-6]) + timedelta(hours=5)).strftime("%Y-%m-%dT%H:%M:%S+00:00")
    exam_data["examStudentReviewStart"] = (datetime.fromisoformat(current_time[:-6]) + timedelta(hours=5)).strftime("%Y-%m-%dT%H:%M:%S+00:00")
    exam_data["examStudentReviewEnd"] = (datetime.fromisoformat(current_time[:-6]) + timedelta(days=1)).strftime("%Y-%m-%dT%H:%M:%S+00:00")

    # Update the exam using PUT method to the correct endpoint
    update_url = f"{SERVER_URL}/exam/courses/{COURSE_ID}/exams"
    response = session.put(update_url, json=exam_data)

    if response.status_code != 200:
        logging.error(f"Failed to end exam. Status code: {response.status_code}\nResponse: {response.text}")
        return

    logging.info(f"Exam {exam_id} ended at {current_time}")

    # Also publish the results immediately after ending the exam
    publish_scores(session, exam_id)

def generate_student_exams(session: requests.Session, exam_id: int) -> None:
    """Generate student exams for all registered students."""
    url = f"{SERVER_URL}/exam/courses/{COURSE_ID}/exams/{exam_id}/generate-student-exams"

    response = session.post(url)

    if response.status_code != 200:
        logging.error(f"Failed to generate student exams. Status code: {response.status_code}\nResponse: {response.text}")
        return

    logging.info(f"Student exams generated for exam {exam_id}")

def prepare_exercise_start(session: requests.Session, exam_id: int) -> None:
    """Prepare exercise start for the exam."""
    url = f"{SERVER_URL}/exam/courses/{COURSE_ID}/exams/{exam_id}/student-exams/start-exercises"

    response = session.post(url)

    if response.status_code != 200:
        logging.error(f"Failed to prepare exercise start. Status code: {response.status_code}\nResponse: {response.text}")
        return

    logging.info(f"Exercise start prepared for exam {exam_id}")

def set_exam_start_time(session: requests.Session, exam_id: int) -> None:
    """Set exam start time to 1 minute before now."""
    # Calculate times: start 1 minute ago, visible 2 minutes ago, end 30 minutes from now
    now = datetime.now(timezone.utc)
    start_time = now - timedelta(minutes=1)
    visible_time = now - timedelta(minutes=2)
    end_time = now + timedelta(minutes=30)

    # Format times
    start_time_str = start_time.strftime("%Y-%m-%dT%H:%M:%S+00:00")
    visible_time_str = visible_time.strftime("%Y-%m-%dT%H:%M:%S+00:00")
    end_time_str = end_time.strftime("%Y-%m-%dT%H:%M:%S+00:00")

    # Get current exam data
    url = f"{SERVER_URL}/exam/courses/{COURSE_ID}/exams/{exam_id}"
    response = session.get(url)

    if response.status_code != 200:
        logging.error(f"Failed to get exam data. Status code: {response.status_code}\nResponse: {response.text}")
        return

    exam_data = response.json()
    exam_data["startDate"] = start_time_str
    exam_data["visibleDate"] = visible_time_str
    exam_data["endDate"] = end_time_str

    # Update the exam using PUT method to the correct endpoint
    update_url = f"{SERVER_URL}/exam/courses/{COURSE_ID}/exams"
    response = session.put(update_url, json=exam_data)

    if response.status_code != 200:
        logging.error(f"Failed to set exam start time. Status code: {response.status_code}\nResponse: {response.text}")
        return

    logging.info(f"Exam {exam_id} start time set to {start_time_str}")

def publish_scores(session: requests.Session, exam_id: int) -> None:
    """Publish scores for the exam by setting publishResultsDate to current time."""
    current_time = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S+00:00")

    # First get the current exam to update it
    url = f"{SERVER_URL}/exam/courses/{COURSE_ID}/exams/{exam_id}"
    response = session.get(url)

    if response.status_code != 200:
        logging.error(f"Failed to get exam data. Status code: {response.status_code}\nResponse: {response.text}")
        return

    exam_data = response.json()
    exam_data["publishResultsDate"] = current_time

    # Update the exam using PUT method to the correct endpoint
    update_url = f"{SERVER_URL}/exam/courses/{COURSE_ID}/exams"
    response = session.put(update_url, json=exam_data)

    if response.status_code != 200:
        logging.error(f"Failed to publish scores. Status code: {response.status_code}\nResponse: {response.text}")
        return

    logging.info(f"Scores published for exam {exam_id} at {current_time}")

def main() -> None:
    """Main function to run the complete exam workflow."""
    logging.info("=== Starting Exam Workflow ===")

    # Create session and authenticate
    session = requests.Session()
    authenticate_user(ADMIN_USER, ADMIN_PASSWORD, session)

    # Step 1: Create exam
    logging.info("Step 1: Creating exam...")
    exam_id = create_exam_main()

    # Step 2: Generate student exams
    logging.info("Step 2: Generating student exams...")
    generate_student_exams(session, exam_id)

    # Step 3: Prepare exercise start
    logging.info("Step 3: Preparing exercise start...")
    prepare_exercise_start(session, exam_id)

    # Step 4: Set exam start time to 1 minute before now
    logging.info("Step 4: Setting exam start time to 1 minute before now...")
    set_exam_start_time(session, exam_id)

    # Step 5: Wait for exam to be active
    logging.info("Step 5: Waiting 5 seconds for exam to be active...")
    time.sleep(5)

    # Step 6: Get exam exercises
    logging.info("Step 6: Getting exam exercises...")
    exercises = get_exam_exercises(session, exam_id)
    if not exercises:
        logging.error("No exercises found in exam")
        return

    exercise_id = exercises[0]['id']
    logging.info(f"Using exercise ID: {exercise_id}")

    # Step 7: Have students submit exercises
    logging.info("Step 7: Having students submit exercises...")
    students = get_course_students(session)

    # Create threads for student submissions
    threads = []
    for student in students:
        student_username = student.get('login')
        if student_username and student_username.startswith('student'):
            thread = threading.Thread(
                target=student_submit_exercise,
                args=(student_username, "Password123!", exercise_id, exam_id)
            )
            threads.append(thread)
            thread.start()

    # Wait for all threads to complete
    for thread in threads:
        thread.join()

    logging.info("All student submissions completed")

    # Step 8: End exam
    logging.info("Step 8: Ending exam...")
    end_exam(session, exam_id)

    logging.info("=== Exam Workflow Completed Successfully ===")

if __name__ == "__main__":
    main()
