import os
import sys
import requests
import configparser
import json
import urllib3
import re
import time
from logging_config import logging
from requests import Session
from typing import Dict, Any, Union, List
from utils import COURSE_EXERCISES, MAX_THREADS, PECV_BENCH_REPO_URL, SERVER_URL, clone_pecv_bench, create_exercise_variants, get_pecv_bench_dir, install_pecv_bench_dependencies, login_as_admin
from manage_pecv_bench_course import create_pecv_bench_course_request


def create_empty_pecv_bench_course() -> None:
    logging.info("Creating empty PECV Bench Course TEST")
    session = requests.Session()
    login_as_admin(session)
    create_pecv_bench_course_request(session)

if __name__ == "__main__":
    create_empty_pecv_bench_course()