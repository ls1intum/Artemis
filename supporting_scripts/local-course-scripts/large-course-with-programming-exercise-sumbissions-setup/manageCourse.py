import logging
from createStudents import get_user_details_by_index

def add_users_to_groups_of_course(session, course_id, server_url, studentsToCreate):
    """Add users to the specified course."""
    for user_index in range(1, studentsToCreate):
        user_details = get_user_details_by_index(user_index)
        add_user_to_course(session, course_id, user_details["groups"][0], user_details["login"], server_url)

def add_user_to_course(session, course_id, user_group, user_name, server_url):
    url = f"{server_url}/api/courses/{course_id}/{user_group}/{user_name}"
    response = session.post(url)
    if response.status_code == 200:
        logging.info(f"Added user {user_name} to group {user_group}")
    else:
        logging.error(f"Could not add user {user_name} to group {user_group}")
