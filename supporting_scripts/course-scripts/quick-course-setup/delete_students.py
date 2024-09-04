import configparser
import requests
from requests import Session
from logging_config import logging
from utils import authenticate_user, get_student_details_by_index

# Load configuration
config = configparser.ConfigParser()
config.read('config.ini')

# Constants from config file
CLIENT_URL: str = config.get('Settings', 'client_url')
ADMIN_USER: str = config.get('Settings', 'admin_user')
ADMIN_PASSWORD: str = config.get('Settings', 'admin_password')
STUDENTS_TO_CREATE: int = int(config.get('Settings', 'students')) + 1

def delete_all_created_students(session: Session) -> None:
    authenticate_user(ADMIN_USER, ADMIN_PASSWORD, session)
    delete_students(session)
    logging.info(f"Deleted all created students successfully")

def delete_students(session: Session) -> None:
    for user_index in range(1, STUDENTS_TO_CREATE):
        user_details = get_student_details_by_index(user_index)
        delete_student(session, user_details['login'])

def delete_student(session: Session, username: str) -> None:
    url = f"{CLIENT_URL}/admin/users/{username}"
    response = session.delete(url)

    if response.status_code == 200:
        logging.info(f"User {username} was deleted successfully")
    elif response.status_code == 404:
        logging.info(f"User {username} does not exist.")
    else:
        logging.error(f"Deleting {username} failed. Status code: {response.status_code}\nResponse content: {response.text}")

def main() -> None:
    session: Session = requests.session()
    delete_all_created_students(session)

if __name__ == "__main__":
    main()
