import requests
import json
import re
import sys
import argparse
import getpass
import os
import git
from bs4 import BeautifulSoup
import pyperclip
import dotenv

dotenv.load_dotenv()

base_url = "https://bamboo.ase.in.tum.de/"
project_key = "ARTEMIS"
plan_key = "TESTS"
repo_path = "../.."  # relative to this file

def environ_or_required(key):
    # https://stackoverflow.com/a/45392259/4306257
    return (
        {'default': os.environ.get(key)} if os.environ.get(key)
        else {'required': True}
    )

def get_latest_build_id(username, password, branch_name):
    url = f"{base_url}/rest/api/latest/plan/{project_key}-{plan_key}/branch?max-results=100"
    response = requests.get(
        url,
        auth=(username, password),
        headers={"Accept": "application/json"},
    )
    data = json.loads(response.content)
    branches = data["branches"]["branch"]
    branch = next(
        (branch for branch in branches if branch["shortName"] == branch_name.replace("/", "-")), None
    )
    if branch is None:
        print(f"Branch {branch_name} not found")
        sys.exit(1)
    return int(branch["shortKey"].replace("TESTS", ""))

def get_latest_build_number(username, password, build_id):
    url = f"{base_url}/rest/api/latest/result/{project_key}-{plan_key}{build_id}.json"
    response = requests.get(
        url,
        auth=(username, password),
        headers={"Accept": "application/json"},
    )
    data = json.loads(response.content)
    return int(data["results"]["result"][0]["number"])

def get_code_cov_report_url(username, password, key):
    url = f"{base_url}/rest/api/latest/result/{key}.json?expand=artifacts"
    response = requests.get(url, auth=(username, password))
    data = json.loads(response.content)
    return data["artifacts"]["artifact"][1]["link"]["href"]

def get_branch_name():
    repo = git.Repo(repo_path)
    branch_name = repo.active_branch.name
    return branch_name

def get_changed_files(branch_name):
    repo = git.Repo(repo_path)

    branch_head = repo.commit(branch_name)
    branch_base = repo.merge_base(branch_name, "origin/develop")[0]
    diff_index = branch_head.diff(branch_base, create_patch=False)
    file_names = [item.a_path for item in diff_index]

    return file_names

def filter_files(file_names, verbose):
    client_file_names = []
    server_file_names = []

    for file_name in file_names:
        if file_name.startswith("src/main/webapp/app"):
            if file_name.endswith(".ts") and "module.ts" not in file_name:
                client_file_names.append(file_name[len("src/main/webapp/"):])
            elif verbose:
                print(f"Skipping {file_name}")
        elif file_name.startswith("src/main/java/de/tum/in/www1/artemis"):
            if file_name.endswith(".java"):
                server_file_names.append(file_name[len("src/main/java/"):])
            elif verbose:
                print(f"Skipping {file_name}")
        elif verbose:
            print(f"Skipping {file_name}")

    return client_file_names, server_file_names

def get_client_line_coverage(username, password, key, file_name, verbose):
    report_url = get_code_cov_report_url(username, password, key)
    file_report_url = report_url.replace("index", file_name)

    if verbose:
        print(f"Coverage {file_name} -> Requesting ... ", end="")
    response = requests.get(file_report_url, auth=(username, password))
    if verbose:
        print(f"{response.status_code}", end="")

    line_coverage = None
    try:
        soup = BeautifulSoup(response.content, "html.parser")
        coverage_divs = soup.find_all("div", {"class": "fl pad1y space-right2"})
        line_coverage_strong = coverage_divs[3].find("span", {"class": "strong"})
        line_coverage = re.search(r"\d+\.\d+", line_coverage_strong.text).group()
        if verbose:
            print(f" -> line coverage: {line_coverage}")
    except:
        if verbose:
            print(" -> no coverage found")

    return (file_name, file_report_url, line_coverage)

def get_server_line_coverage(username, password, key, file_name, verbose):
    report_url = get_code_cov_report_url(username, password, key)
    (path, class_name) = file_name.replace(".java", "").rsplit("/", 1)
    package = path.replace("/", ".")
    file_name = file_name[len("de/tum/in/www1/") :]
    report_name = f"{package}/{class_name}"

    file_report_url = report_url.replace("index", report_name)
    if verbose:
        print(f"Coverage {file_name} -> Requesting ... ", end="")
    response = requests.get(file_report_url, auth=(username, password))
    if verbose:
        print(f"{response.status_code}", end="")

    line_coverage = None
    try:
        soup = BeautifulSoup(response.content, "html.parser")
        tfoot = soup.find("tfoot")
        ctr2_tds = tfoot.find_all("td", class_="ctr2")
        line_coverage = ctr2_tds[0].text[:-1]
        if verbose:
            print(f" -> line coverage: {line_coverage}")
    except:
        if verbose:
            print(" -> no coverage found")

    return (file_name, file_report_url, line_coverage)

def coverage_to_table(covs):
    header = "| Class/File | Line Coverage | Confirmation (assert/expect) |\n|------------|--------------:|---------------------:|"
    table_data = []

    for cov in covs:
        filename_only = cov[0].rsplit("/", 1)[1]
        class_file = f"[{filename_only}]({cov[1]})"
        line_coverage = cov[2] if cov[2] is not None else "N/A"
        confirmation = "✅❌" if cov[2] is not None else "❌"
        table_data.append(f"| {class_file} | {line_coverage}% | {confirmation} |")

    table = "\n".join([header] + table_data)
    return table

def main(argv):
    parser = argparse.ArgumentParser(
        description="Generate code coverage report for changed files in a Bamboo build\n\nAuthor: Felix Dietrich",
        formatter_class=argparse.RawTextHelpFormatter,
    )
    parser.add_argument(
        "--username", help="Bamboo username",
        **environ_or_required("BAMBOO_USERNAME")
    )
    parser.add_argument(
        "--password", help="Bamboo password (optional, will be prompted if not provided)",
        **environ_or_required("BAMBOO_PASSWORD")
    )
    parser.add_argument(
        "--branch-name", default=None, help="Name of the Git branch you want to compare against develop (will checkout the branch)",
    )
    parser.add_argument(
        "--build-id", default=None, help="Build ID of the Bamboo build (ARTEMIS-TESTS{BUILD_ID}-JAVATEST-{BUILD_NUMBER})"
    )
    parser.add_argument(
        "--build-number", default=None, help="Build number (ARTEMIS-TESTS{BUILD_ID}-JAVATEST-{BUILD_NUMBER})"
    )
    parser.add_argument(
        "--verbose", action="store_true", help="Enable verbose logging"
    )

    args = parser.parse_args(argv)
    if args.branch_name is None:
        args.branch_name = get_branch_name()
        print(f"Using current branch: {args.branch_name}")
    if args.build_id is None:
        args.build_id = get_latest_build_id(args.username, args.password, args.branch_name)
        print(f"Using latest build ID: {args.build_id}")
    if args.build_number is None:
        args.build_number = get_latest_build_number(args.username, args.password, args.build_id)
        print(f"Using latest build number: {args.build_number}")
    if args.password is None:
        args.password = getpass.getpass("Please enter your Bamboo password: ")

    project_key = "ARTEMIS-TESTS"

    server_key = f"{project_key}{args.build_id}-JAVATEST-{args.build_number}"
    client_key = f"{project_key}{args.build_id}-TSTEST-{args.build_number}"

    file_names = get_changed_files(args.branch_name)
    client_file_names, server_file_names = filter_files(file_names, args.verbose)

    client_cov = [
        get_client_line_coverage(args.username, args.password, client_key, file_name, args.verbose)
        for file_name in client_file_names
    ]
    server_cov = [
        get_server_line_coverage(args.username, args.password, server_key, file_name, args.verbose)
        for file_name in server_file_names
    ]

    client_table = coverage_to_table(client_cov)
    server_table = coverage_to_table(server_cov)

    result = ""
    if client_file_names:
        result += f"#### Client\n\n{client_table}\n\n"
    if server_file_names:
        result += f"#### Server\n\n{server_table}\n\n"

    pyperclip.copy(result)
    print("Code coverage report copied to clipboard.")

if __name__ == "__main__":
    main(sys.argv[1:])
