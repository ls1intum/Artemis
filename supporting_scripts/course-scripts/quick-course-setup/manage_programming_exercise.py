from logging_config import logging
from typing import List, Dict, Any
from requests import Session

exercise_ids: List[int] = []

def create_programming_exercise(session: Session, course_id: int, server_url: str, exercises_to_create: int) -> None:
    """Create multiple programming exercises for the course."""
    for i in range(exercises_to_create):
        url: str = f"{server_url}/programming-exercises/setup"
        headers: Dict[str, str] = {"Content-Type": "application/json"}
        short_name_index: int = i + 1

        default_programming_exercise: Dict[str, Any] = {
            "type": "programming",
            "title": f"Example Programming Exercise {short_name_index}",
            "shortName": f"ExProgEx{short_name_index}",
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
                "testwiseCoverageEnabled": False
            },
        }

        response = session.post(url, json=default_programming_exercise, headers=headers)

        if response.status_code == 201:
            logging.info(f"Created programming exercise {default_programming_exercise['title']} successfully")
            exercise_ids.append(response.json().get('id'))
        else:
            raise Exception(f"Could not create programming exercise; Status code: {response.status_code}\nResponse content: {response.text}")

def add_participation(session: Session, exercise_id: int, client_url: str) -> Dict[str, Any]:
    """Add a participation for the exercise."""
    url: str = f"{client_url}/exercises/{exercise_id}/participations"
    headers: Dict[str, str] = {"Content-Type": "application/json"}

    response = session.post(url, headers=headers)
    if response.status_code == 201:
        return response.json()
    else:
        response.raise_for_status()

def commit(session: Session, participation_id: int, client_url: str, commits_per_student: int) -> None:
    """Commit the participation to the repository multiple times."""
    for _ in range(commits_per_student):
        url: str = f"{client_url}/repository/{participation_id}/commit"
        headers: Dict[str, str] = {"Content-Type": "application/json"}

        response = session.post(url, headers=headers)
        if response.status_code != 201:
            response.raise_for_status()
