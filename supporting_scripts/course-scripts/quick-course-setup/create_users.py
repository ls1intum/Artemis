import requests
import configparser
from logging_config import logging
from requests import Session
from typing import List, Tuple

from authenticate_all_users import authenticate_all_generated_users
from utils import login_as_admin, get_user_details_by_index, get_student_details_by_index

# Load configuration
config = configparser.ConfigParser()
config.read('config.ini')

# Constants from config file
client_url: str = config.get('Settings', 'client_url')

# Store user credentials
user_credentials: List[Tuple[str, str]] = []

def make_create_user_post_request(session: Session, user_details: dict) -> None:
    """Send a POST request to create a user."""
    url = f"{client_url}/admin/users"
    headers = {"Content-Type": "application/json"}
    response = session.post(url, json=user_details, headers=headers)

    if response.status_code == 201:
        logging.info(f"{user_details['login']} was created successfully")
        print(f"{user_details['login']} was created successfully")
        user_credentials.append((user_details['login'], user_details['password']))
    elif response.status_code == 400 and "userExists" in response.json().get("errorKey", ""):
        logging.info(f"User {user_details['login']} already exists.")
        user_credentials.append((user_details['login'], user_details['password']))
    else:
        raise Exception(
            f"Creating {user_details['login']} failed. Status code: {response.status_code}\n"
            f"Response content: {response.text}"
        )

def create_course_users(session: Session) -> None:
    """Create a predefined set of course users."""
    for userIndex in range(1, 21):
        user_details = get_user_details_by_index(userIndex)
        make_create_user_post_request(session, user_details)

def create_cypress_users(session: Session) -> None:
    """Create a predefined set of Cypress users."""
    for userIndex in range(100, 107):
        user_details = get_user_details_by_index(userIndex)
        make_create_user_post_request(session, user_details)

def create_users(session: Session) -> None:
    """Create all predefined users including course and Cypress users."""
    create_course_users(session)
    create_cypress_users(session)

def create_students(session: Session, students_to_create: int) -> None:
    """Create multiple students and store their credentials."""
    for user_index in range(1, students_to_create):
        user_details = get_student_details_by_index(user_index)
        make_create_user_post_request(session, user_details)

def main() -> None:
    """Main function to create users and authenticate them."""
    session = requests.session()
    login_as_admin(session)

    try:
        create_users(session)
    except Exception as exception:
        error_message = str(exception)
        if "Login name already used!" in error_message:
            print("Users already created. Continuing ...")
        else:
            raise

    create_users(session)
    authenticate_all_generated_users()

if __name__ == "__main__":
    # DO ONLY USE FOR LOCAL COURSE SETUP!
    # (Otherwise users will be created for whom the credentials are public in the repository!)
    main()
