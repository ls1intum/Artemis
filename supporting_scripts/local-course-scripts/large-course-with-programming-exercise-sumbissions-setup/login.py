import requests
import logging

def authenticate_user(username, password, server_url, session=requests.Session()):
    url = f"{server_url}/api/public/authenticate"
    headers = {
        "Content-Type": "application/json"
    }

    payload = {
        "username": username,
        "password": password,
        "rememberMe": True
    }

    response = session.post(url, json=payload, headers=headers)

    if response.status_code == 200:
        logging.info(f"Authentication successful for user {username}")
    else:
        raise Exception(
            f"Authentication failed for user {username}. Status code: {response.status_code}\n Response content: {response.text}")

    return response
