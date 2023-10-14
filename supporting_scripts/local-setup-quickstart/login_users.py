import requests
from login_users.py import get_user_details_by_index


def authenticate_user(username, password):
    url = "http://localhost:9000/api/public/authenticate"
    headers = {
        "Content-Type": "application/json"
    }

    payload = {
        "username": username,
        "password": password,
        "rememberMe": True
    }

    response = requests.post(url, json=payload, headers=headers)

    if response.status_code == 200:
        print(f"Authentication successful for user {username}")
    else:
        print(f"Authentication failed for user {username}. Status code:", response.status_code)
        print("Response content: {}", response.text)


def authenticate_all_generated_users_for_first_time():
    """
    Users are only visible in the course once they have logged in for the first time

    This method logs in the users that are generated in Jira with the script for the Atlassian Setup:
    See https://github.com/ls1intum/Artemis/blob/develop/docker/atlassian/atlassian-setup.sh
    """
    for user_index in range(1, 21):
        user_details = get_user_details_by_index(user_index)
        authenticate_user(user_details.username, user_details.password)

    # login cypress users
    for user_index in range(100, 104):
        user_details = get_user_details_by_index(user_index)
        authenticate_user(user_details.username, user_details.password)

    user_details = get_user_details_by_index(106)
    authenticate_user(user_details.username, user_details.password)


def main():
    authenticate_all_generated_users_for_first_time()


if __name__ == "__main__":
    main()
