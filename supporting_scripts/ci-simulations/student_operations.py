from requests import Session
import configparser
from logging_config import logging

config = configparser.ConfigParser()
config.read('config.ini')
SERVER_URL: str = config.get('Settings', 'server_url')

def participate_programming_exercise(session: Session, exercise_id: str, files_to_commit: dict[str, str] | None = None, commits: int = 1):
    """Make a student participate in a programming exercise."""
    participation_url = f"{SERVER_URL}/exercise/exercises/{exercise_id}/participations"
    participation_response = session.post(participation_url)
    if participation_response.status_code != 201:
        logging.error(f"Failed to create participation. Status: {participation_response.status_code} {participation_response.text}")
        return

    participation = participation_response.json()
    participation_id = participation.get('id')

    for _ in range(0, commits):
        if files_to_commit:
            url = f"{SERVER_URL}/programming/repository/{participation_id}/files?commit=true"
            file_submissions = [{
                "fileName": file_name,
                "fileContent": file_content
            } for file_name, file_content in files_to_commit.items()]
            response = session.put(url, json=file_submissions)
            if response.status_code != 200:
                logging.error(f"Failed to commit files. Status: {response.status_code}")
                return
        else:
            commit_url = f"{SERVER_URL}/programming/repository/{participation_id}/commit"
            commit_response = session.post(commit_url)
            if commit_response.status_code not in [200, 201]:
                logging.error(f"Failed to commit. Status: {commit_response.status_code}")
                return
        
    logging.debug(f"Student successfully committed exercise {exercise_id}")
    return participation