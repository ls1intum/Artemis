from utils import authenticate_user, get_user_details_by_index

def authenticate_all_generated_users() -> None:
    """
    Users are only visible in the course once they have logged in for the first time.

    This method logs in the users that are generated in Jira with the script for the Atlassian Setup:
    See https://github.com/ls1intum/Artemis/blob/develop/docker/atlassian/atlassian-setup.sh

    or from the script "create_users.py"
    """
    for user_index in range(1, 21):
        user_details = get_user_details_by_index(user_index)
        authenticate_user(user_details['login'], user_details['password'])

def main() -> None:
    authenticate_all_generated_users()

if __name__ == "__main__":
    main()
