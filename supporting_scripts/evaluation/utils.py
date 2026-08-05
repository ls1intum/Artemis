"""Configuration and authenticated session handling for the variant-generation evaluation.

Mirrors supporting_scripts/hyperion/consistency-check-benchmark/utils.py: credentials live in an
untracked config.ini next to this module, never in a committed file.
"""

import configparser
import os
import sys
from typing import Any, Dict

import requests

from logging_config import logging

HERE = os.path.dirname(os.path.abspath(__file__))
CONFIG_PATH = os.path.join(HERE, "config.ini")

config = configparser.ConfigParser()
if not os.path.exists(CONFIG_PATH):
    logging.critical("config.ini not found next to utils.py — copy config.ini.example and fill it in.")
    sys.exit(1)
config.read([CONFIG_PATH])

USER: str = config.get("Settings", "user")
PASSWORD: str = config.get("Settings", "password")
SERVER_URL: str = config.get("Settings", "server_url").rstrip("/")

COURSE_NAME: str = config.get("Corpus", "course_name")
COURSE_SHORT_NAME: str = config.get("Corpus", "course_short_name")
PROGRAMMING_TITLE: str = config.get("Corpus", "programming_title")
PROGRAMMING_SHORT_NAME: str = config.get("Corpus", "programming_short_name")
PACKAGE_NAME: str = config.get("Corpus", "package_name")
QUIZ_TITLE: str = config.get("Corpus", "quiz_title")

INSTANCE_LOG_GLOB: str = os.path.normpath(os.path.join(HERE, config.get("Run", "instance_log_glob")))
CONCURRENCY: int = config.getint("Run", "concurrency")
TIMEOUT_SECONDS: int = config.getint("Run", "timeout_seconds")
POLL_SECONDS: float = config.getfloat("Run", "poll_seconds")

CORPUS_PATH = os.path.join(HERE, "corpus", "corpus.json")


def authenticated_session() -> requests.Session:
    """Log in once and return the session; every run reuses it (jobs are per-user scoped)."""
    session = requests.Session()
    payload: Dict[str, Any] = {"username": USER, "password": PASSWORD, "rememberMe": True}
    response = session.post(f"{SERVER_URL}/core/public/authenticate", json=payload)
    if response.status_code != 200:
        raise RuntimeError(f"Authentication failed for {USER}: {response.status_code} {response.text}")
    logging.info("Authenticated as %s against %s", USER, SERVER_URL)
    return session


def expect(response: requests.Response, *accepted: int) -> requests.Response:
    """Raise with the response body attached unless the status code is one of ``accepted``."""
    if response.status_code not in accepted:
        raise RuntimeError(f"{response.request.method} {response.request.url} -> {response.status_code}: {response.text[:2000]}")
    return response