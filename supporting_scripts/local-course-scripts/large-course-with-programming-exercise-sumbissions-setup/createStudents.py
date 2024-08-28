import logging

user_credentials = []

def create_users(session, client_url, students_To_Create):
    """Create multiple users and store their credentials."""
    for user_index in range(1, students_To_Create):
        user_details = get_user_details_by_index(user_index)
        make_create_user_post_request(session, user_details, client_url)

def make_create_user_post_request(session, user_details, client_url):
    """Send a POST request to create a user."""
    url = f"{client_url}/api/admin/users"
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
        raise Exception(f"Creating {user_details['login']} failed. Status code: {response.status_code}\nResponse content: {response.text}")

def get_user_details_by_index(user_index):
    """Generate user details based on the index."""
    username = f"student{user_index}"
    password = "Password123!"
    user_role = "ROLE_USER"
    authorities = [user_role]
    groups = ["students"]

    return {
        "activated": True,
        "authorities": authorities,
        "login": username,
        "email": f"{username}@example.com",
        "firstName": "Test",
        "lastName": f"User{user_index}",
        "langKey": "en",
        "guidedTourSettings": [],
        "groups": groups,
        "password": password
    }
