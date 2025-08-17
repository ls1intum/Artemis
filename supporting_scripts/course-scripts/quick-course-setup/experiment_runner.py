import configparser
import logging
from concurrent.futures import ThreadPoolExecutor, as_completed
import time
from uuid import uuid4
import json
from typing import Any, Callable, Dict, Tuple, List
from requests import Session
from requests.adapters import HTTPAdapter
from student_operations import participate_programming_exercise
from ssh_helper import run_ssh_command
from utils import add_user_to_course, login_as_admin, authenticate_user
from add_users_to_course import add_users_to_groups_of_course
from manage_programming_exercise import create_single_programming_exercise
from admin_operations import (
    get_build_job_statistics_for_course,
    get_queued_build_jobs_for_course,
    get_running_build_jobs_for_course,
    get_server_artemis_version_info,
    get_server_info,
    get_submissions_for_exercise,
    get_all_results,
    log_build_agent_summaries,
    get_build_agents,
)
from experiment_config import ExperimentConfig
import urllib3
from urllib3.util.retry import Retry

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
    """Handles experiment operations and session management."""
    def __init__(self):
        self.admin_session = None
        self.user_sessions = {}
        self.course_id = None
        self.exercise_id = None
        self.polling_data = []
        self.executed_remote_command_at = None

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
        self.configure_session_for_high_concurrency(session)
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
        max_workers: int = 20,
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

    def _collect_polling_data_point(self, start_time: float):
        current_time = time.time()
        elapsed_time = current_time - start_time
        logging.debug(f"Starting to poll data at {current_time}")
        submissions = get_submissions_for_exercise(self.admin_session, self.exercise_id)
        results = get_all_results(submissions)
        running_jobs = get_running_build_jobs_for_course(self.admin_session, self.course_id)
        queued_jobs = get_queued_build_jobs_for_course(self.admin_session, self.course_id)
        build_agents = get_build_agents(self.admin_session)
        log_build_agent_summaries(build_agents)

        build_agent_data = []
        for agent in build_agents:
            agent_info = {
                'name': agent.get('buildAgent', {}).get('displayName', 'Unknown'),
                'status': agent.get('status', 'Unknown'),
                'numberOfCurrentBuildJobs': agent.get('numberOfCurrentBuildJobs', 0)
            }
            build_agent_data.append(agent_info)

        data_point = {
            'timestamp': current_time,
            'elapsed_time_seconds': elapsed_time,
            'submissions_count': len(submissions),
            'results_count': len(results),
            'running_jobs_count': len(running_jobs),
            'queued_jobs_count': len(queued_jobs),
            'build_agents': build_agent_data
        }
        self.polling_data.append(data_point)
        return data_point

    def get_current_results(self):
        submissions = get_submissions_for_exercise(self.admin_session, self.exercise_id)
        results = get_all_results(submissions)
        return results

    def poll_job_completions(
        self,
        start_time: float,
        expected_submissions: int,
        timeout_seconds: int = 60 * 15,
        interval_seconds: int = 10,
    ) -> List[Dict[str, Any]]:
        """Poll for job completions and collect data for plotting."""
        logging.info(f"Polling job completions for exercise {self.exercise_id}...")
        self.polling_data = []

        while time.time() - start_time < timeout_seconds:
            poll_start = time.time()
            data_point = self._collect_polling_data_point(start_time)

            logging.info(
                f"[T+{data_point['elapsed_time_seconds']:.0f}s] "
                f"Submissions: {data_point['submissions_count']}, Results: {data_point['results_count']}, "
                f"Running: {data_point['running_jobs_count']}, Queued: {data_point['queued_jobs_count']}, "
            )
            if data_point['submissions_count'] == expected_submissions and data_point["queued_jobs_count"] == 0 and data_point and data_point['running_jobs_count'] == 0:
                logging.info("No jobs processing or queued anymore. Returning results")
                return self.get_current_results()

            if data_point['results_count'] == expected_submissions:
                logging.info(
                    f"All submissions have results. Returning {data_point['results_count']} results."
                )
                return self.get_current_results()
            poll_duration = time.time() - poll_start
            # make the polling interval roughly constant by adjusting it for total duration taken to fetch data
            time.sleep(max(interval_seconds - poll_duration, 0))

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

    def setup_experiment(self, experiment_config: ExperimentConfig, number_of_students: int, experiment_id: str = str(uuid4())):
        """Set up the entire experiment."""
        self.admin_session = Session()
        self.configure_session_for_high_concurrency(self.admin_session)
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

    def run_experiment(self, experiment_config: ExperimentConfig, number_of_commits: int = 1):
        """Run the experiment with students participating."""
        logging.info(f"Running {experiment_config.identifier} experiment with {len(self.user_sessions)} students.")
        if experiment_config.remote_command:
            logging.info(f"Remote command to be executed: {experiment_config.remote_command} after {experiment_config.execute_after_seconds} seconds on {experiment_config.get_target_node_address()}")

        version_info = get_server_artemis_version_info(self.admin_session)
        initial_sleep = 10
        start_time = time.time()
        self._collect_polling_data_point(start_time)
        time.sleep(initial_sleep) # Avoid instantly starting the submissions

        with ThreadPoolExecutor(max_workers=2) as executor:
            student_operations_future = executor.submit(
                self.run_parallel_student_operations,
                participate_programming_exercise,
                exercise_id=self.exercise_id,
                files_to_commit=experiment_config.commit_files,
                commits=number_of_commits,
            )
            remote_future = None
            remote_node_execute = experiment_config.get_target_node_address()
            if remote_node_execute:
                remote_future = executor.submit(self.execute_remote_command, experiment_config, remote_node_execute)

            results = self.poll_job_completions(
                start_time=start_time,
                expected_submissions=len(self.user_sessions) * number_of_commits,
                timeout_seconds=experiment_config.timeout_experiment,
                interval_seconds=10,
            )

            try:
                student_operations_future.result(timeout=10)
                logging.info(f"Student operations completed successfully")
            except Exception as e:
                logging.error(f"Student operations encountered an error: {e}")
            try:
                if remote_future is not None:
                    remote_future.result(timeout=60)
                    logging.info(f"Remote command executed successfully")
                else:
                    logging.debug("No remote command was executed")
            except Exception as e:
                logging.error(f"Remote command encountered an error: {e}")

        logging.info(f"Results collected: {len(results)} results for {len(self.user_sessions)} students.")
        end_time = time.time()
        logging.info(f"Experiment completed in {(end_time - start_time - initial_sleep):.2f} seconds")

        time.sleep(10) # wait fot the stats to update
        stats = get_build_job_statistics_for_course(self.admin_session, self.course_id)
        logging.info(f"Build job statistics for course {self.course_id}: {stats}")

        start_ms = int(start_time * 1000 - 30 * 1000) # -30 seconds for visualization
        end_ms = int(end_time * 1000 + 30 * 1000) # + 30 seconds for visualization
        base_url = "https://grafana.gchq.ase.in.tum.de/d/aeefmxka3vgg0a/artemis-dev-cluster-artemis-node-overview"
        params = f"?orgId=1&from={start_ms}&to={end_ms}"
        grafana_link = f"{base_url}{params}"

        filename_base = f"test_reports/polling_data_{time.strftime('%Y%m%d_%H%M%S')}-{version_info}-{experiment_config.identifier}-{len(self.user_sessions)}x{number_of_commits}"
        file_Name = self.save_polling_data(filename_base=filename_base, grafana_link=grafana_link, experiment_config=experiment_config if remote_node_execute else None, start=start_time, end=end_time, initial_sleep=initial_sleep, stats=stats)
        self._generate_experiment_plots(filename=file_Name)


        if remote_node_execute and experiment_config.final_command:
            logging.info(f"Executing final command: {experiment_config.final_command} on node {remote_node_execute}")
            run_ssh_command(remote_node_execute, experiment_config.final_command, verbose=True)

        return results, stats

    def execute_remote_command(self, experiment_config: ExperimentConfig, remote_node_execute: str):
        command = experiment_config.remote_command
        if command:
            time.sleep(experiment_config.execute_after_seconds)
            logging.info(f"Trying to execute remote command: {command} on {remote_node_execute} after {experiment_config.execute_after_seconds} seconds")
            self.executed_remote_command_at = time.time()
            return run_ssh_command(remote_node_execute, command, verbose=True)

    def save_polling_data(self, filename_base: str, grafana_link: str = None, experiment_config: ExperimentConfig = None, start: float = None, end: float = None, initial_sleep: float = None, stats: Dict[str, Any] = None):
        """Save the collected polling data to a JSON file."""
        import os
        os.makedirs(os.path.dirname(filename_base), exist_ok=True)

        json_filename = filename_base + ".json"
        with open(json_filename, 'w') as f:
            json.dump(self.polling_data, f, indent=2)
        logging.info(f"Polling data saved to {json_filename}")

        if grafana_link:
            logging.info(f"Grafana: {grafana_link}")
            with open(filename_base + "_grafana.txt", 'a') as f:
                f.write(f"\nGrafana link: {grafana_link}\n")
        if stats:
            with open(filename_base + "_stats.json", 'a') as f:
                json.dump(stats, f, indent=2)
        if experiment_config:
            info = get_server_info(self.admin_session)
            profiles = info.get("activeProfiles", [])
            with open(filename_base + "_timing_info.json", 'a') as f:
                json.dump({
                    "start_time": start,
                    "end_time": end,
                    "initial_sleep": initial_sleep,
                    "injected_command_at": self.executed_remote_command_at,
                    "injected_command": experiment_config.remote_command,
                    "injected_command_after": experiment_config.execute_after_seconds,
                    "serverProfiles": profiles
                }, f)

        return json_filename

    def _generate_experiment_plots(self, filename:str):
        """Automatically generate plots for the experiment data."""
        try:
            import subprocess
            import os
            plot_script = "plot_experiment_data.py"
            if os.path.exists(plot_script):
                logging.info(f"Generating plots for {filename}...")
                result = subprocess.run(
                    ["python", plot_script, filename],
                    capture_output=True,
                    text=True,
                    cwd=os.getcwd()
                )

                if result.returncode == 0:
                    logging.info("Plots generated successfully!")
                else:
                    logging.error(f"Failed to generate plots: {result.stderr} {result.stdout}")
            else:
                logging.warning(f"Plot script {plot_script} not found")

        except Exception as e:
            logging.error(f"Error generating plots: {e}")

    def configure_session_for_high_concurrency(self, session: Session, pool_connections=50, pool_maxsize=100):
        """Configure a session for high concurrency with larger connection pools."""
        adapter = HTTPAdapter(
            pool_connections=pool_connections,  # Number of connection pools
            pool_maxsize=pool_maxsize,         # Max connections per pool
            max_retries=Retry(
                total=5,
                backoff_factor=0.3,
                status_forcelist=[500, 502, 503, 504]
            )
        )
        session.mount('http://', adapter)
        session.mount('https://', adapter)
        return session
