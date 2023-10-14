import requests


def make_create_user_post_request(user_details):
    url = "http://localhost:9000/api/public/authenticate"
    headers = {
        "Content-Type": "application/json"
    }
    payload = user_details
    response = requests.post(url, json=payload, headers=headers)

    if response.status_code == 201:
        print(f"{user_details.username} was created successfully")
    else:
        print(f"Creating {user_details.username} failed. Status code:", response.status_code)
        print("Response content: {}", response.text)


def get_user_details_by_index(user_index):
    username = f"artemis_test_user_{user_index}"
    password = username
    authorities = []
    groups = []

    if 1 <= user_index <= 5:
        authorities = ["ROLE_USER"]
        groups = ["students"]
    elif 6 <= user_index <= 10:
        authorities = ["ROLE_TA"]
        groups = ["tutors"]
    elif 11 <= user_index <= 15:
        authorities = ["ROLE_EDITOR"]
        groups = ["editors"]
    elif 16 <= user_index <= 20:
        authorities = ["ROLE_INSTRUCTOR"]
        groups = ["instructors"]
    # the following users are test users needed for executing the cypress tests
    elif user_index in {100, 102, 104, 106}:
        authorities = ["ROLE_USER"]
        groups = ["students"]
    elif user_index == 103:
        authorities = ["ROLE_INSTRUCTOR"]
        groups = ["instructors"]
    elif user_index == 101:
        authorities = ["ROLE_TA"]
        groups = ["tutors"]

    return {
        "activated": True,
        "authorities": authorities,
        "login": username,
        "email": username + "@artemis.local",
        "firstName": username,
        "lastName": username,
        "langKey": "en",
        "guidedTourSettings": [],
        "groups": groups,
        "password": password
    }


def create_course_users():
    for userIndex in range(1, 21):
        get_user_details_by_index(userIndex)


def create_cypress_users():
    for userIndex in range(100, 104):
        get_user_details_by_index(userIndex)
    get_user_details_by_index(106)


def create_users():
    create_course_users()
    create_cypress_users()


def main():
    # TODO create course first, otherwise the usergroup student might not exist
    create_users()


if __name__ == "__main__":
    main()
