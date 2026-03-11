import shutil
import os
import json
from typing import Any, Dict, List
import zipfile
from logging_config import logging
from exercises import read_problem_statement, sanitize_exercise_name

# ======= TEST FUNCTIONS =======
def test_convert_base_exercise_to_zip(exercise_path: str, course_id: int) -> None:
    """
    Converts a base programming exercise (no variants) into a ZIP file using a random unique ID.

    :param str exercise_path: The path to the base exercise directory on your computer. Should contain solution/, template/, tests/ folders, problem-statement.md and exercise-details.json file.
    :param int course_id: The ID of the course to which the exercise belongs. Can be obtained via API request (e.g Postman).
    Or simply calling get_course_id_request function after logging in, which will retrieve the course id based on the course name defined in config.ini.

    :return: None
    :rtype: None
    """
    base_repo_name = os.path.basename(exercise_path)
    repo_types: List[str] = ["solution", "template", "tests"]
    config_file: str = "exercise-details.json"
    variant_id = 0
    exercise_zip_filename = f"{variant_id}-FullExercise.zip"
    exercise_zip_path = os.path.join(exercise_path, exercise_zip_filename)

    logging.info(f"Final zip file: {exercise_zip_filename} will be created at {exercise_zip_path}")

    zip_files = []
    try:
        for repo_type in repo_types:
            repo_name = f"{variant_id}-{repo_type}"
            base_repo_name = os.path.join(exercise_path, repo_name)
            repo_path = os.path.join(exercise_path, repo_type)
            if not os.path.exists(repo_path):
                logging.error(f"Required folder {repo_type} does not exist in {exercise_path}.")
                continue
            zip_folder_path = shutil.make_archive(base_name = base_repo_name, format = 'zip', root_dir = repo_path)
            zip_files.append(zip_folder_path)
    except Exception as e:
        logging.exception(f"Error while creating intermediate zip files: {e}")
        return None

    problem_statement_file_path = os.path.join(exercise_path, "problem-statement.md")
    problem_statement_content = None
    if os.path.exists(problem_statement_file_path):
        problem_statement_content = read_problem_statement(problem_statement_file_path)

    config_file_path = os.path.join(exercise_path, config_file)
    try:
        with open(config_file_path, 'r', encoding='utf-8') as cf:
            exercise_details: Dict[str, Any] = json.load(cf)
            exercise_details['id'] = None
            if problem_statement_content is not None:
                exercise_details['problemStatement'] = problem_statement_content
            course_name = ""
            if 'course' in exercise_details:
                exercise_details['course']['id'] = course_id
                course_name = exercise_details['course'].get('shortName', '')
            exercise_name = exercise_details.get('title', 'Untitled')
            exercise_details['title'] = f"{variant_id} - {exercise_name}"
            exercise_details['shortName'] = sanitize_exercise_name(exercise_name, int(variant_id))
            exercise_details["projectKey"] = f"{variant_id}{course_name}{exercise_details['shortName']}"
        with open(config_file_path, 'w', encoding='utf-8') as cf:
            json.dump(exercise_details, cf, indent=4)
    except Exception as e:
        logging.error(f"Failed to update config file: {e}")
        return None
    zip_files.append(config_file_path)

    with zipfile.ZipFile(exercise_zip_path, 'w', zipfile.ZIP_DEFLATED) as zipf:
        for file in zip_files:
            if 'template' in file:
                new_name = os.path.join(exercise_path, f"{variant_id}-exercise.zip")
                os.rename(file, new_name)
                zipf.write(new_name, arcname=f"{variant_id}-exercise.zip")
                continue
            arcname = "Exercise-Details.json" if os.path.basename(file).lower() == config_file.lower() else os.path.basename(file)
            zipf.write(file, arcname=arcname)

    zip_files.append(os.path.join(exercise_path, f"{variant_id}-exercise.zip"))
    for temp_zip in zip_files:
        if temp_zip.endswith('.zip') and os.path.exists(temp_zip):
            os.remove(temp_zip)

# ==============================

if __name__ == "__main__":
    # Example usage:
    exercise_path = "/path/to/base/exercise"
    course_id = 123
    test_convert_base_exercise_to_zip(exercise_path, course_id)
