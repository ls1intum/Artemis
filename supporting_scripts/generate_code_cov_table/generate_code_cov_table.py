import json
import sys
import argparse
import getpass
import os
import logging

from tqdm.auto import tqdm
import requests
import git
from bs4 import BeautifulSoup
import pyperclip
import dotenv

dotenv.load_dotenv()
logging.basicConfig(level=logging.INFO, format="%(message)s")
logging.getLogger("requests").setLevel(logging.WARNING)
logging.getLogger("urllib3").setLevel(logging.WARNING)

base_url = "https://bamboo.ase.in.tum.de/"
project_key = "ARTEMIS"
plan_key = "TESTS"
repo_path = "../.."  # relative to this file


def environ_or_required(key, required=True):
    # https://stackoverflow.com/a/45392259/4306257
    return (
        {'default': os.environ[key]} if key in os.environ and os.environ[key] != ""
        else {'required': required}
    )


def get_latest_build_id(username, password, branch_name):
    bamboo_branch_name = branch_name.replace("origin/", "").replace("/", "-")
    url = f"{base_url}/rest/api/latest/plan/{project_key}-{plan_key}/branch/{bamboo_branch_name}.json"
    response = requests.get(url, auth=(username, password))
    if response.status_code == 200:
        branch = json.loads(response.content)
        return int(branch["shortKey"].replace("TESTS", ""))
    elif response.status_code == 404:
        logging.error(f"Branch {branch_name} not found in Bamboo")
        sys.exit(1)
    else:
        logging.error(f"Error accessing Bamboo with status code {response.status_code}")
        sys.exit(1)

    branch = json.loads(response.content)
    return int(branch["shortKey"].replace("TESTS", ""))


def get_latest_build_number(username, password, build_id):
    url = f"{base_url}/rest/api/latest/result/{project_key}-{plan_key}{build_id}.json"
    response = requests.get(url, auth=(username, password))
    data = json.loads(response.content)
    return int(data["results"]["result"][0]["number"])


def get_code_cov_report_url(username, password, key):
    url = f"{base_url}/rest/api/latest/result/{key}.json?expand=artifacts"
    response = requests.get(url, auth=(username, password))
    data = json.loads(response.content)
    try:
        return data["artifacts"]["artifact"][1]["link"]["href"] # second artifact is the code coverage report
    except IndexError:
        logging.warning(f"Code coverage report not found for {key}, please wait for the tests to finish and try again")
        sys.exit(1)


def get_branch_name():
    repo = git.Repo(repo_path)
    return repo.active_branch.name


def get_changed_files(branch_name, base_branch_name="origin/develop"):
    repo = git.Repo(repo_path)
    branch_head = repo.commit(branch_name)
    branch_base = repo.merge_base(branch_name, base_branch_name)[0]
    diff_index = branch_head.diff(branch_base, create_patch=False)
    file_names = [item.a_path for item in diff_index]

    return file_names


def filter_files(file_names):
    client_file_names = []
    server_file_names = []

    for file_name in file_names:
        if file_name.startswith("src/main/webapp/app"):
            if file_name.endswith(".ts") and "module.ts" not in file_name:
                client_file_names.append(file_name[len("src/main/webapp/"):])
                continue
        elif file_name.startswith("src/main/java/de/tum/in/www1/artemis"):
            if file_name.endswith(".java"):
                server_file_names.append(file_name[len("src/main/java/"):])
                continue
        logging.debug(f"Skipping {file_name}")
    return client_file_names, server_file_names


def get_client_line_coverage(username, password, key, file_name):
    report_url = get_code_cov_report_url(username, password, key)
    file_report_url = report_url.replace("index", file_name)
    response = requests.get(file_report_url, auth=(username, password))

    soup = BeautifulSoup(response.content, "html.parser")
    coverage_divs = soup.find_all("div", {"class": "fl pad1y space-right2"})
    line_coverage = None
    if len(coverage_divs) >= 4:
        line_coverage_strong = coverage_divs[3].find("span", {"class": "strong"})
        line_coverage = line_coverage_strong.text.replace("%", "").strip()

    logging.debug(f"Coverage for {file_name} -> GET report -> {response.status_code} -> line coverage: {line_coverage}")
    return file_name, file_report_url, line_coverage


def get_server_line_coverage(username, password, key, file_name):
    report_url = get_code_cov_report_url(username, password, key)
    path, class_name = file_name.replace(".java", "").rsplit("/", 1)
    package = path.replace("/", ".")
    file_name = file_name[len("de/tum/in/www1/") :]
    report_name = f"{package}/{class_name}"

    file_report_url = report_url.replace("index", report_name)
    response = requests.get(file_report_url, auth=(username, password))

    soup = BeautifulSoup(response.content, "html.parser")
    line_coverage = None
    tfoot = soup.find("tfoot")
    if tfoot:
        ctr2_tds = tfoot.find_all("td", class_="ctr2")
        if ctr2_tds and len(ctr2_tds) > 0:
            line_coverage = ctr2_tds[0].text.replace("%", "").strip()

    logging.debug(f"Coverage for {file_name} -> GET report -> {response.status_code} -> line coverage: {line_coverage}")
    return file_name, file_report_url, line_coverage


def coverage_to_table(covs, exclude_urls=False):
    header = "| Class/File | Line Coverage | Confirmation (assert/expect) |\n|------------|--------------:|---------------------:|"
    table_data = []

    for cov in covs:
        filename_only = cov[0].rsplit("/", 1)[1]
        class_file = filename_only if exclude_urls else f"[{filename_only}]({cov[1]})"
        line_coverage = "N/A" if cov[2] is None else cov[2]
        confirmation = "❌" if cov[2] is None else "✅❌"
        table_data.append(f"| {class_file} | {line_coverage}% | {confirmation} |")

    table = "\n".join([header] + table_data)
    return table


def main(argv):
    parser = argparse.ArgumentParser(
        description="Generate code coverage report for changed files in a Bamboo build.",
        formatter_class=argparse.RawTextHelpFormatter,
    )
    parser.add_argument(
        "--username", help="Bamboo username",
        **environ_or_required("BAMBOO_USERNAME")
    )
    parser.add_argument(
        "--password", help="Bamboo password (optional, will be prompted if not provided)",
        **environ_or_required("BAMBOO_PASSWORD", required=False)
    )
    parser.add_argument(
        "--branch-name", default=None, help="Name of the Git branch you want to compare with the base branch (default: origin/develop)"
    )
    parser.add_argument(
        "--base-branch-name", default="origin/develop", help="Name of the Git base branch (default: origin/develop)"
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
    parser.add_argument(
        "--exclude-urls", action="store_true",
        help="Exclude URLs from the report"
    )
    parser.add_argument(
        "--print-results", action="store_true",
        help="Print the report to console instead of copying to clipboard"
    )

    args = parser.parse_args(argv)
    if args.verbose:
        logging.getLogger().setLevel(logging.DEBUG)
    if args.password is None:
        args.password = getpass.getpass("Please enter your Bamboo password: ")
    if args.branch_name is None:
        args.branch_name = get_branch_name()
        logging.info(f"Using current branch: {args.branch_name}")
    if args.build_id is None:
        args.build_id = get_latest_build_id(args.username, args.password, args.branch_name)
        logging.info(f"Using latest build ID: {args.build_id}")
    if args.build_number is None:
        args.build_number = get_latest_build_number(args.username, args.password, args.build_id)
        logging.info(f"Using latest build number: {args.build_number}")

    project_key = "ARTEMIS-TESTS"

    server_key = f"{project_key}{args.build_id}-JAVATEST-{args.build_number}"
    client_key = f"{project_key}{args.build_id}-TSTEST-{args.build_number}"

    file_names = get_changed_files(args.branch_name, args.base_branch_name)
    client_file_names, server_file_names = filter_files(file_names)

    client_cov = [
        get_client_line_coverage(args.username, args.password, client_key, file_name)
        for file_name in tqdm(client_file_names, desc="Fetching client coverage", unit="files")
    ]
    server_cov = [
        get_server_line_coverage(args.username, args.password, server_key, file_name)
        for file_name in tqdm(server_file_names, desc="Fetching server coverage", unit="files")
    ]

    client_table = coverage_to_table(client_cov, args.exclude_urls)
    server_table = coverage_to_table(server_cov, args.exclude_urls)

    result = ""
    if client_file_names:
        result += f"#### Client\n\n{client_table}\n\n"
    if server_file_names:
        result += f"#### Server\n\n{server_table}\n\n"

    logging.info("Info: ✅❌ in Confirmation (assert/expect) have to be adjusted manually, also delete trivial files!")
    logging.info("") # newline

    if args.print_results:
        print(result)
    else:
        result_utf16 = result.encode('utf-16le') + b'\x00\x00'
        pyperclip.copy(result_utf16.decode('utf-16le'))
        logging.info("Code coverage report copied to clipboard. Use --print-results to print it to console instead.")


if __name__ == "__main__":
    main(sys.argv[1:])
