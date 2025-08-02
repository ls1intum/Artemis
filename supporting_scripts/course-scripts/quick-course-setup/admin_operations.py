from typing import Any, Dict, List
from requests import Session
from logging_config import logging
import configparser

config = configparser.ConfigParser()
config.read('config.ini')
SERVER_URL: str = config.get('Settings', 'server_url')

def get_submissions_for_exercise(session: Session, exercise_id: int):
    participations = get_participations(session, exercise_id)
    submissions = []
    for participation in participations:
        submissions.extend(get_submissions(session, participation["id"]))
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

def log_build_agent_summaries(session: Session): 
    build_agents = get_build_agents(session)
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