import json
import pytest
from uuid import uuid4
from requests import Session
import time
import configparser
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import Dict, Callable, Any, List, Tuple
from logging_config import logging
from utils import add_user_to_course, login_as_admin, authenticate_user
from add_users_to_course import add_users_to_groups_of_course
from manage_programming_exercise import create_single_programming_exercise
from admin_operations import (
    get_build_job_statistics_for_course,
    get_queued_build_jobs_for_course,
    get_running_build_jobs_for_course,
    get_submissions_for_exercise,
    get_all_results,
    log_build_agent_summaries,
)
from experiment_config import JAVA_HAPPY_PATH, JAVA_TIMEOUT_BUILD, JAVA_FAILING_BUILD, C_HAPPY_PATH, ExperimentConfig
from student_operations import participate_programming_exercise
import urllib3

config = configparser.ConfigParser()
config.read("config.ini")
secrets = configparser.ConfigParser()
secrets.read("secrets.ini")

SERVER_URL: str = config.get("Settings", "server_url")
CLIENT_URL: str = config.get("Settings", "client_url")

USER_NAME_PATTERN: str = secrets.get(
    "User", "artemis_test_user_pattern", fallback="artemis_test_user_{}"
)
PASSWORD_PATTERN: str = secrets.get(
    "User", "artemis_test_user_password_pattern", fallback="artemis_test_user_{}"
)


class ExperimentRunner:
    """Class to handle experiment operations and session management."""
    def __init__(self):
        self.admin_session = None
        self.user_sessions = {}
        self.course_id = None
        self.exercise_id = None

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.cleanup()

    def cleanup(self):
        logging.info("Cleaning up sessions...")
        if self.admin_session:
            self.admin_session.close()
        for session in self.user_sessions.values():
            if session:
                session.close()
        logging.info("Cleanup completed.")

    def authenticate_single_user(self, user_index: int) -> tuple[int, Session]:
        session = Session()
        username = USER_NAME_PATTERN.format(user_index)
        password = PASSWORD_PATTERN.format(user_index)

        try:
            authenticate_user(username, password, session)
            return user_index, session
        except Exception as e:
            logging.error(f"Failed to authenticate user {username}: {e}")
            return user_index, None

    def authenticate_users(self, user_count: int, max_workers=4) -> Dict[int, Session]:
        user_sessions = {}
        test_user_range = range(1, user_count + 1)
        
        with ThreadPoolExecutor(max_workers=max_workers) as executor:
            try:
                future_to_user = {
                    executor.submit(self.authenticate_single_user, i): i
                    for i in test_user_range
                }

                for future in as_completed(future_to_user):
                    user_index, session = future.result()
                    if session is not None:
                        user_sessions[user_index] = session
                    else:
                        logging.error(f"Failed to store session for user {user_index}")
            except Exception as e:
                logging.error(f"Error during parallel authentication: {e}")
                for session in user_sessions.values():
                    if session:
                        session.close()
                raise

        logging.info(
            f"Parallel authentication completed. {len(user_sessions)} users authenticated successfully."
        )
        self.user_sessions = user_sessions
        return user_sessions

    def run_operation_for_student(
        self, user_index: int, session: Session, operation: Callable, *args, **kwargs
    ) -> Tuple[int, Any]:
        """Execute an operation for a single student and return the result."""
        try:
            result = operation(session, *args, **kwargs)
            logging.debug(f"Operation completed successfully for user {user_index}")
            return user_index, result
        except Exception as e:
            logging.error(f"Operation failed for user {user_index}: {e}")
            return user_index, None

    def run_parallel_student_operations(
        self,
        operation: Callable,
        max_workers: int = 10,
        *args,
        **kwargs,
    ) -> Dict[int, Any]:
        """Run an operation in parallel for all authenticated students."""
        results = {}

        logging.info(f"Starting parallel operation for {len(self.user_sessions)} students...")
        
        with ThreadPoolExecutor(max_workers=max_workers) as executor:
            try:
                future_to_user = {
                    executor.submit(
                        self.run_operation_for_student,
                        user_index,
                        session,
                        operation,
                        *args,
                        **kwargs,
                    ): user_index
                    for user_index, session in self.user_sessions.items()
                }

                for future in as_completed(future_to_user):
                    user_index, result = future.result()
                    results[user_index] = result
                    if result is not None:
                        logging.debug(f"Operation successful for user {user_index}")
                    else:
                        logging.error(f"Operation failed for user {user_index}")
            except Exception as e:
                logging.error(f"Error during parallel student operations: {e}")
                raise

        successful_operations = sum(1 for result in results.values() if result is not None)
        logging.info(
            f"Parallel operation completed. {successful_operations}/{len(self.user_sessions)} operations successful."
        )

        return results

    def poll_job_completions(
        self,
        timeout_seconds: int = 600,
        interval_seconds: int = 10,
    ) -> List[Dict[str, Any]]:
        """Poll for job completions."""
        logging.info(f"Polling job completions for exercise {self.exercise_id}...")
        start_time = time.time()

        logging.info("Waiting for submissions to be created...")
        while time.time() - start_time < timeout_seconds:
            submissions = get_submissions_for_exercise(self.admin_session, self.exercise_id)
            if len(submissions) > 0:
                logging.info(
                    f"Found {len(submissions)} submissions, starting to poll for results..."
                )
                break
            logging.debug("No submissions found yet, waiting...")
            time.sleep(interval_seconds)

        while time.time() - start_time < timeout_seconds:
            submissions = get_submissions_for_exercise(self.admin_session, self.exercise_id)
            results = get_all_results(submissions)
            logging.info(
                f"Current submissions: {len(submissions)}, current results: {len(results)}"
            )

            running_jobs = get_running_build_jobs_for_course(self.admin_session, self.course_id)
            logging.info(
                f"Currently running jobs for course {self.course_id}: {len(running_jobs)}"
            )

            queued_jobs = get_queued_build_jobs_for_course(self.admin_session, self.course_id)
            logging.info(
                f"Currently queued jobs for course {self.course_id}: {len(queued_jobs)}"
            )

            log_build_agent_summaries(self.admin_session)

            if len(results) == len(submissions):
                logging.info(
                    f"All submissions have results. Returning {len(results)} results."
                )
                return results
            time.sleep(interval_seconds)

        submissions = get_submissions_for_exercise(self.admin_session, self.exercise_id)
        results = get_all_results(submissions)
        logging.warning(
            f"Timeout reached after {timeout_seconds} seconds. Returning {len(results)} results for {len(submissions)} submissions."
        )
        return results

    def create_course(self, title: str, short_name: str) -> Dict[str, Any]:
        """Create a course using the admin session."""
        url = f"{SERVER_URL}/core/admin/courses"
        course_data = {
            "title": title,
            "shortName": short_name,
        }
        fields = {"course": ("blob.json", json.dumps(course_data), "application/json")}

        body, content_type = urllib3.filepost.encode_multipart_formdata(fields)
        headers = {"Content-Type": content_type}
        response = self.admin_session.post(url, data=body, headers=headers)

        if response.status_code == 201:
            course = response.json()
            self.course_id = course.get('id')
            logging.info(f"Course created successfully with ID: {self.course_id}")
            return course
        else:
            raise Exception(
                f"Could not create course; Status code: {response.status_code}\nResponse content: {response.text}"
            )

    def register_students_to_course(self, user_count: int):
        """Register students to the course."""
        def register_user(user_index):
            add_user_to_course(
                self.admin_session, self.course_id, "students", USER_NAME_PATTERN.format(user_index)
            )

        with ThreadPoolExecutor() as executor:
            executor.map(register_user, range(1, user_count + 1))

    def create_programming_exercise(self, experiment_config: ExperimentConfig, experiment_id: str):
        """Create a programming exercise."""
        exercise = create_single_programming_exercise(
            self.admin_session,
            self.course_id,
            SERVER_URL,
            f"Test Experiment Exercise {experiment_id}",
            experiment_config.package_name,
            programming_language=experiment_config.programming_language,
            project_type=experiment_config.project_type,
            build_script=experiment_config.build_script,
        )
        self.exercise_id = exercise.get("id")
        logging.info(
            f"Using programming exercise: {self.exercise_id} for experiments. \n check: {CLIENT_URL}/course-management/{self.course_id}/exercises"
        )
        return exercise

    def setup_experiment(self, experiment_config: ExperimentConfig, number_of_students: int, experiment_id: str):
        """Set up the entire experiment."""
        self.admin_session = Session()
        login_as_admin(self.admin_session)

        course = self.create_course(
            f"Experiment Test Course {experiment_id}",
            f"course{experiment_id[:5]}",
        )
        self.create_programming_exercise(experiment_config, experiment_id)
        self.register_students_to_course(number_of_students)
        logging.info(f"Using course: {self.course_id} for experiments")

        self.authenticate_users(number_of_students, max_workers=min(number_of_students, 10))

        return course

    def run_experiment(self, experiment_config, number_of_commits: int = 1):
        """Run the experiment with students participating."""
        start_time = time.time()
        self.run_parallel_student_operations(
            participate_programming_exercise,
            exercise_id=self.exercise_id,
            files_to_commit=experiment_config.commit_files,
            commits=number_of_commits,
        )

        results = self.poll_job_completions(
            timeout_seconds=60 * 20,
            interval_seconds=15,
        )
        end_time = time.time()
        logging.info(f"Experiment completed in {end_time - start_time:.2f} seconds")
        stats = get_build_job_statistics_for_course(self.admin_session, self.course_id)
        
        logging.info(
            f"Results collected: {len(results)} results for {len(self.user_sessions)} students."
        )
        logging.info(f"Build job statistics for course {self.course_id}: {stats}")

        return results, stats

def assertStatsForPassingJobs(stats, number_of_students, number_of_commits):
    assert stats is not None, "Should have build statistics"
    assert stats.get('totalBuilds', 0) == number_of_students * number_of_commits + 2, "Number of build jobs should match the number of student commits +2 for solution and template"
    assert stats.get('successfulBuilds', 0) == number_of_students * number_of_commits + 2, "All build jobs should be successful"
    assert stats.get('missingBuilds', 0) == 0, "There should be no missing builds"
   

@pytest.fixture
def experiment_runner():
    """Fixture to provide an ExperimentRunner instance with automatic cleanup."""
    with ExperimentRunner() as runner:
        yield runner

def test_java_failing_builds_high_load(experiment_runner: ExperimentRunner):
    experiment_id = str(uuid4())
    number_of_students = 100
    number_of_commits = 5
    
    logging.info(f"Running Java failing build test with {number_of_students} students")

    experiment_runner.setup_experiment(JAVA_FAILING_BUILD, number_of_students, experiment_id)
    results, stats = experiment_runner.run_experiment(JAVA_FAILING_BUILD, number_of_commits)
    
    # Specific assertions for failing builds
    assert len(results) == number_of_students * number_of_commits, "All submissions should have results"
    assert stats is not None, "Should have build statistics"


def test_java_happy_path_medium_load(experiment_runner: ExperimentRunner):
    """Test Java happy path with medium load."""
    experiment_id = str(uuid4())
    number_of_students = 250
    number_of_commits = 1
    
    logging.info(f"Running Java happy path test with {number_of_students} students")
    
    course = experiment_runner.setup_experiment(JAVA_HAPPY_PATH, number_of_students, experiment_id)
    results, stats = experiment_runner.run_experiment(JAVA_HAPPY_PATH, number_of_commits)
    
    assert len(results) == number_of_students * number_of_commits, "All submissions should have results"
    for result in results:
        assert result.get('rated') is True, "All results should be rated"
        assert result.get('score') > 0, "All results should have a positive partial score"

    assertStatsForPassingJobs(stats, number_of_students, number_of_commits)

def test_c_happy_path_high_load(experiment_runner: ExperimentRunner):
    """Test C happy path with medium load."""
    experiment_id = str(uuid4())
    number_of_students = 500
    number_of_commits = 1

    logging.info(f"Running C happy path test with {number_of_students} students")
    course = experiment_runner.setup_experiment(C_HAPPY_PATH, number_of_students, experiment_id)
    results, stats = experiment_runner.run_experiment(C_HAPPY_PATH, number_of_commits)

    assert len(results) == number_of_students * number_of_commits, "All submissions should have results"
    for result in results:
        assert result.get('rated') is True, "All results should be rated"
        assert result.get('score') > 0, "All results should have a positive partial score"
        assert result.get('successful') is True, "All result have full score"
    assertStatsForPassingJobs(stats, number_of_students, number_of_commits)