import logging

def create_programming_exercise(session, course_id, server_url, short_name_index):
    """Create a programming exercise for the course."""
    url = f"{server_url}/api/programming-exercises/setup"
    headers = {"Content-Type": "application/json"}

    programming_exercise = {
        "type": "programming",
        "title": "Example Programming Exercise" + str(short_name_index),
        "shortName": "ExProgEx" + str(short_name_index),
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

    response = session.post(url, json=programming_exercise, headers=headers)

    if response.status_code == 201:
        logging.info(f"Created programming exercise {programming_exercise['title']} successfully")
    else:
        raise Exception(f"Could not create programming exercise; Status code: {response.status_code}\nResponse content: {response.text}")

    return response

def add_participation(session, exercise_id, client_url):
    """Add a participation for the exercise."""
    url = f"{client_url}/api/exercises/{exercise_id}/participations"
    headers = {"Content-Type": "application/json"}

    response = session.post(url, headers=headers)
    if response.status_code == 201:
        return response.json()
    else:
        response.raise_for_status()

def commit(session, participation_id, client_url):
    """Commit the participation to the repository."""
    url = f"{client_url}/api/repository/{participation_id}/commit"
    headers = {"Content-Type": "application/json"}

    response = session.post(url, headers=headers)
    if response.status_code == 201:
        return response.json()
    else:
        response.raise_for_status()
