import sys
import re
from logging_config import logging
from typing import Dict, Any
from requests import Session

exercise_Ids: list[int] = []

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


def create_programming_exercise(session: Session, course_id: int, server_url: str, exercises_to_create: int, exercise_name: str) -> None:
    """Create multiple programming exercises for the course."""
    for i in range(exercises_to_create):
        url: str = f"{server_url}/programming/programming-exercises/setup"
        headers: Dict[str, str] = {"Content-Type": "application/json"}
        short_name_index: int = i + 1

        short_name = sanitize_exercise_name_with_index(exercise_name, short_name_index)

        default_programming_exercise: Dict[str, Any] = {
            "type": "programming",
            "title": f"{exercise_name}",
            "shortName": short_name,
            "course": {"id": course_id},
            "programmingLanguage": "JAVA",
            "projectType": "PLAIN_GRADLE",
            "allowOnlineEditor": True,
            "allowOfflineIde": True,
            "maxPoints": 100,
            "assessmentType": "AUTOMATIC",
            "packageName": "de.tum.in.www1.example",
            "staticCodeAnalysisEnabled": False,
            "buildConfig": {
                "buildScript": "#!/usr/bin/env bash\nset -e\n\ngradle () {\n  echo '⚙️ executing gradle'\n  chmod +x ./gradlew\n  ./gradlew clean test\n}\n\nmain () {\n  gradle\n}\n\nmain \"${@}\"\n",
                "checkoutSolutionRepository": False,
            },
        }

        response = session.post(url, json=default_programming_exercise, headers=headers)

        if response.status_code == 201:
            logging.info(f"Created programming exercise {default_programming_exercise['title']} successfully")
            exercise_Ids.append(response.json().get('id'))
        elif response.status_code == 400:
            logging.info(f"Programming exercise with shortName {default_programming_exercise['shortName']} already exists. Please provide the exercise IDs in the config file and set create_exercises to FALSE.")
            #run_cleanup()
            sys.exit(0)
        else:
            raise Exception(f"Could not create programming exercise; Status code: {response.status_code}\nResponse content: {response.text}")

def add_participation(session: Session, exercise_id: int, client_url: str) -> Dict[str, Any]:
    """Add a participation for the exercise."""
    url: str = f"{client_url}/exercise/exercises/{exercise_id}/participations"
    headers: Dict[str, str] = {"Content-Type": "application/json"}

    response = session.post(url, headers=headers)
    if response.status_code == 201:
        return response.json()
    elif response.status_code == 403:
        logging.info(f"Not allowed to push to following programming exercise with following id: {exercise_id}. Please double check if the exercise is part of the Course and update the exercise_Ids in the config file.")
        sys.exit(0)
    else:
        response.raise_for_status()

def commit(session: Session, participation_id: int, client_url: str, commits_per_student: int) -> None:
    """Commit the participation to the repository multiple times."""
    for _ in range(commits_per_student):
        url: str = f"{client_url}/programming/repository/{participation_id}/commit"
        headers: Dict[str, str] = {"Content-Type": "application/json"}

        response = session.post(url, headers=headers)
        if response.status_code != 201:
            response.raise_for_status()
