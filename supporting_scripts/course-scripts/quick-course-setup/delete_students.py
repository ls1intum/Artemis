from logging_config import logging
from requests import Session
from create_users import user_credentials

def delete_students(session: Session, client_url: str) -> None:
    """Delete multiple users based on their credentials."""
    for username, _ in user_credentials:
        delete_student(session, username, client_url)

def delete_student(session: Session, username: str, client_url: str) -> None:
    """Send a DELETE request to delete a user."""
    url = f"{client_url}/admin/users/{username}"
    response = session.delete(url)

    if response.status_code == 200:
        logging.info(f"User {username} was deleted successfully")
    elif response.status_code == 404:
        logging.info(f"User {username} does not exist.")
    else:
        logging.error(f"Deleting {username} failed. Status code: {response.status_code}\nResponse content: {response.text}")
