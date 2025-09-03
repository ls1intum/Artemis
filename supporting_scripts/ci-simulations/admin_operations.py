import time
from typing import Any, Dict, List
from requests import Session
from logging_config import logging
import configparser
from concurrent.futures import ThreadPoolExecutor, as_completed
import re

config = configparser.ConfigParser()
config.read('config.ini')
SERVER_URL: str = config.get('Settings', 'server_url')


def sanitize_exercise_name(exercise_name: str) -> str:
    """Sanitize the exercise name to create a valid short name."""
    valid_short_name = re.sub(r'[^a-zA-Z0-9]', '', exercise_name)
    if not valid_short_name or not valid_short_name[0].isalpha():
        valid_short_name = f"A{valid_short_name}"
    return valid_short_name

def sanitize_exercise_name_with_index(exercise_name: str, short_name_index: int) -> str:
    return f"{sanitize_exercise_name(exercise_name)}{short_name_index}"

def create_single_programming_exercise(session: Session, course_id: int, server_url: str, exercise_name: str, package_name: str, programming_language: str = "JAVA", project_type: str = "PLAIN_GRADLE", build_script: str | None = None):
    url: str = f"{server_url}/programming/programming-exercises/setup"
    headers: Dict[str, str] = {"Content-Type": "application/json"}
    short_name = sanitize_exercise_name(exercise_name)

    default_programming_exercise: Dict[str, Any] = {
        "type": "programming",
        "title": f"{exercise_name}",
        "shortName": short_name,
        "course": {"id": course_id},
        "programmingLanguage": programming_language,
        "projectType": project_type,
        "allowOnlineEditor": True,
        "allowOfflineIde": True,
        "maxPoints": 100,
        "assessmentType": "AUTOMATIC",
        "packageName": package_name,
        "staticCodeAnalysisEnabled": False,
        "buildConfig": {
            "buildScript": build_script if build_script else "#!/usr/bin/env bash\nset -e\n\ngradle () {\n  echo '⚙️ executing gradle'\n  chmod +x ./gradlew\n  ./gradlew clean test\n}\n\nmain () {\n  gradle\n}\n\nmain \"${@}\"\n",
            "checkoutSolutionRepository": False,
        },
    }
    response = session.post(url, json=default_programming_exercise, headers=headers)

    if response.status_code == 201:
        logging.info(f"Created programming exercise {default_programming_exercise['title']} with id {response.json().get('id')} successfully")
        return response.json()
    elif response.status_code == 400:
        logging.info(f"Programming exercise with shortName {default_programming_exercise['shortName']} already exists. Please provide the exercise IDs in the config file and set create_exercises to FALSE.")
    raise Exception(f"Could not create programming exercise; Status code: {response.status_code}\nResponse content: {response.text}")

def get_submissions_for_exercise(session: Session, exercise_id: int):
    start_time = time.time()
    participations = get_participations(session, exercise_id)
    
    if not participations:
        return []
    
    submissions = []
    max_workers = min(20, len(participations)) 
    
    with ThreadPoolExecutor(max_workers=max_workers) as executor:
        future_to_participation = {
            executor.submit(get_submissions, session, participation["id"]): participation["id"]
            for participation in participations
        }
        for future in as_completed(future_to_participation):
            try:
                participation_submissions = future.result()
                submissions.extend(participation_submissions)
            except Exception as e:
                participation_id = future_to_participation[future]
                logging.error(f"Failed to get submissions for participation {participation_id}: {e}")

    logging.info(f"Retrieved {len(submissions)} submissions from {len(participations)} participations in {time.time() - start_time:.2f} seconds")
    return submissions

def get_participations(session: Session, exercise_id: int):
    participation_url = f"{SERVER_URL}/exercise/exercises/{exercise_id}/participations"
    response = session.get(participation_url)
    if response.status_code != 200:
        logging.error(f"Failed to get participations for exercise {exercise_id}, {response.text}")
    return response.json()

def get_submissions(session: Session, participation_id: int):
    submissions_url = f"{SERVER_URL}/exercise/participations/{participation_id}/submissions"
    response = session.get(submissions_url)
    if response.status_code != 200:
        logging.error(f"Failed to get submissions for participation {participation_id}, {response.text}")
    return response.json()

def get_results(submission: Dict[str, Any]) -> List[Dict[str, Any]]:
    """Extract results from a submission."""
    results = []
    if 'results' in submission and submission['results']:
        results.extend(submission['results'])
    return results

def get_all_results(submissions: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """Extract results from submissions."""
    results = []
    for submission in submissions:
        results.extend(get_results(submission))
    return results

def log_build_agent_summaries(build_agents: List[Dict[str, Any]]) -> None: 
    for agent in sorted(build_agents, key=lambda x: x.get('buildAgent', {}).get('displayName', '')):
        logging.info(get_build_agent_summary_str(agent))

def get_build_agent_summary_str(build_agent: Dict[str, Any]) -> str:
   return f"Build Agent {build_agent.get('buildAgent').get('displayName')} - Status: {build_agent.get('status', 'Unknown')} - Currently processing {build_agent.get('numberOfCurrentBuildJobs', 0)} jobs"

def get_build_agents(session: Session) -> List[Dict[str, Any]]:
    """Get the list of build agents."""
    url = f"{SERVER_URL}/core/admin/build-agents"
    response = session.get(url)
    if response.status_code != 200:
        logging.error(f"Failed to get build agents: {response.text}")
        return []
    return response.json()

def filter_build_agents_with_status(build_agents: List[Dict[str, Any]], status: str) -> List[Dict[str, Any]]:
    """Filter build agents by their status."""
    filtered_agents = [agent for agent in build_agents if agent.get('status') == status]
    logging.debug(f"Found {len(filtered_agents)} build agents with status '{status}'.")
    return filtered_agents

def get_queued_build_jobs_for_course(session: Session, course_id: int) -> List[Dict[str, Any]]:
    """Get all queued jobs for a course."""
    url = f"{SERVER_URL}/programming/courses/{course_id}/queued-jobs"
    response = session.get(url)
    if response.status_code != 200:
        logging.error(f"Failed to get queued build jobs for course {course_id}: {response.text}")
        return []
    return response.json()

def get_running_build_jobs_for_course(session: Session, course_id: int) -> List[Dict[str, Any]]:
    """Get all running jobs for a course."""
    url = f"{SERVER_URL}/programming/courses/{course_id}/running-jobs"
    response = session.get(url)
    if response.status_code != 200:
        logging.error(f"Failed to get running build jobs for course {course_id}: {response.text}")
        return []
    return response.json()

# TODO needs pagable
def get_finished_build_jobs_for_course(session: Session, course_id: int) -> List[Dict[str, Any]]:
    """Get all finished jobs for a course."""
    url = f"{SERVER_URL}/programming/courses/{course_id}/finished-jobs"
    response = session.get(url)
    if response.status_code != 200:
        logging.error(f"Failed to get finished build jobs for course {course_id}: {response.text}")
        return []
    return response.json()

def get_build_job_statistics_for_course(session: Session, course_id: int) -> Dict[str, Any]:
    """Get build job statistics for a course."""
    url = f"{SERVER_URL}/programming/courses/{course_id}/build-job-statistics"
    response = session.get(url)
    if response.status_code != 200:
        logging.error(f"Failed to get build job statistics for course {course_id}: {response.text}")
        return {}
    return response.json()

def get_server_info(session: Session) -> Dict[str, Any]:
    """Get Artemis server info."""
    url = f"{SERVER_URL}/management/info"
    url = url.replace("/api", "") # hack to remove /api prefix without introducing extra config values for it
    response = session.get(url)
    if response.status_code != 200:
        logging.error(f"Failed to get server info: {response.text}")
        return {}
    return response.json()

def get_server_artemis_version_info(session: Session) -> Dict[str, Any]:
    """Get Artemis server version info."""
    info = get_server_info(session)
    branch = info.get("git", {}).get("branch", "unknownBranch")
    build = info.get("git", {}).get("build", "").get("version", "unknownVersion")
    return f"{branch}-{build}".replace("/", "-")