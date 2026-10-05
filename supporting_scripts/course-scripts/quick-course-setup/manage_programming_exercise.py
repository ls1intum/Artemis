import sys
import re
from logging_config import logging
from typing import Dict, Any
from requests import Session

from utils import is_local_ci

exercise_Ids: list[int] = []

def sanitize_exercise_name(exercise_name: str, short_name_index: int) -> str:
    """Sanitize the exercise name to create a valid short name."""
    valid_short_name = re.sub(r'[^a-zA-Z0-9]', '', exercise_name)
    if not valid_short_name or not valid_short_name[0].isalpha():
        valid_short_name = f"A{valid_short_name}"
    return f"{valid_short_name}{short_name_index}"

# Build script used for Jenkins-backed servers. LocalCI servers reject a build script outright,
# because there the build is described by the build plan instead, so this is only sent when the
# target server is not running LocalCI.
JENKINS_BUILD_SCRIPT: str = (
    "#!/usr/bin/env bash\nset -e\n\ngradle () {\n  echo '⚙️ executing gradle'\n  chmod +x ./gradlew\n"
    "  ./gradlew clean test\n}\n\nmain () {\n  gradle\n}\n\nmain \"${@}\"\n"
)

def create_programming_exercise(session: Session, course_id: int, server_url: str, exercises_to_create: int, exercise_name: str) -> None:
    """Create multiple programming exercises for the course."""
    # Asked once rather than per exercise: the answer cannot change during a run.
    local_ci: bool = is_local_ci(session)
    logging.info(f"Target server runs {'LocalCI' if local_ci else 'an external CI system (e.g. Jenkins)'}")

    for i in range(exercises_to_create):
        url: str = f"{server_url}/programming/programming-exercises/setup"
        headers: Dict[str, str] = {"Content-Type": "application/json"}
        short_name_index: int = i + 1

        short_name = sanitize_exercise_name(exercise_name, short_name_index)

        build_config: Dict[str, Any] = {"checkoutSolutionRepository": False}
        if not local_ci:
            build_config["buildScript"] = JENKINS_BUILD_SCRIPT

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
            "buildConfig": build_config,
        }

        # Provisioning a programming exercise creates repositories and a build plan, so the read timeout is
        # generous; the point is that a hung server ends the run with an error rather than blocking forever.
        response = session.post(url, json=default_programming_exercise, headers=headers, timeout=(10, 120))

        if response.status_code == 201:
            logging.info(f"Created programming exercise {default_programming_exercise['title']} successfully")
            exercise_Ids.append(response.json().get('id'))
            continue

        # Report what the server actually said. A 400 is not necessarily a duplicate short name, and
        # treating every 400 as one used to end the run with a success exit code and nothing created.
        error_key: str = ""
        message: str = response.text
        try:
            body: Dict[str, Any] = response.json()
            error_key = str(body.get("errorKey") or "")
            message = str(body.get("title") or body.get("detail") or response.text)
        except ValueError:
            pass

        # `vcsProjectExists` / `ciProjectExists` are what the server returns when a project for this
        # title and short name is already present (ProgrammingExerciseValidationService).
        if response.status_code == 400 and error_key in {"vcsProjectExists", "ciProjectExists"}:
            logging.error(
                f"A programming exercise named '{default_programming_exercise['title']}' (shortName "
                f"{short_name}) already exists in course {course_id}. Set create_exercises to False and list the "
                f"existing exercise IDs in exercise_Ids, or choose a different exercise_name."
            )
            sys.exit(1)

        raise Exception(
            f"Could not create programming exercise '{default_programming_exercise['title']}'.\n"
            f"Status code: {response.status_code}\nerrorKey: {error_key or '(none)'}\nMessage: {message}"
        )

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
        url: str = f"{client_url}/programming/participations/{participation_id}/repository/commit"
        headers: Dict[str, str] = {"Content-Type": "application/json"}

        response = session.post(url, headers=headers)
        if response.status_code != 201:
            response.raise_for_status()
