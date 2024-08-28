import logging
import requests
import configparser
from createStudents import user_credentials

def delete_users(session, client_url):
    """Delete multiple users based on their credentials."""
    for username, _ in user_credentials:
        delete_user(session, username, client_url)

def delete_user(session, username, client_url):
    """Send a DELETE request to delete a user."""
    url = f"{client_url}/api/admin/users/{username}"
    response = session.delete(url)

    if response.status_code == 200:
        logging.info(f"User {username} was deleted successfully")
    elif response.status_code == 404:
        logging.info(f"User {username} does not exist.")
    else:
        logging.error(f"Deleting {username} failed. Status code: {response.status_code}\nResponse content: {response.text}")
