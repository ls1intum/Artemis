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
import zipfile
from io import BytesIO

dotenv.load_dotenv()
logging.basicConfig(level=logging.INFO, format="%(message)s")
logging.getLogger("requests").setLevel(logging.WARNING)
logging.getLogger("urllib3").setLevel(logging.WARNING)

repo = "Artemis"
owner = "ls1intum"
repo_path = "../.."  # relative to this file
runs_url = f'https://api.github.com/repos/{owner}/{repo}/actions/runs'

server_tests_key = 'Coverage Report Server Tests'
client_tests_key = 'Coverage Report Client Tests'


def environ_or_required(key, required=True):
    # https://stackoverflow.com/a/45392259/4306257
    return (
        {'default': os.environ[key]} if key in os.environ and os.environ[key] != ""
        else {'required': required}
    )


def download_and_extract_zip(url, headers, key):
    if url is None:
        return None
    try:
        response = requests.get(url, headers=headers, stream=True)
        response.raise_for_status()
        total_size = int(response.headers.get('content-length', 0))
    except requests.RequestException:
        logging.error(f"Failed to process ZIP file from {url}. The content might not be a valid ZIP format or is corrupted.")
        return None

    try:
        chunk_size = 1024
        data_stream = BytesIO()
        with tqdm(total=total_size, unit='B', unit_scale=True, desc="Downloading ZIP for: " + key) as progress_bar:
            for chunk in response.iter_content(chunk_size=chunk_size):
                data_stream.write(chunk)
                progress_bar.update(len(chunk))
        data_stream.seek(0)
        zip_test = zipfile.ZipFile(data_stream)
        zip_test.close()
        data_stream.seek(0)
        return data_stream
    except zipfile.BadZipFile:
        logging.error("The downloaded content is not a valid ZIP file.")
        return None


def get_html_content(zip_file_bytes, file_name):
    try:
        with zipfile.ZipFile(zip_file_bytes, 'r') as zip_ref:
            with zip_ref.open(file_name + '.html') as file_data:
                return file_data.read().decode('utf-8')
    except KeyError:
        logging.error(f"File {file_name} not found in the archive.")
        return None
    except zipfile.BadZipFile:
        logging.error("The provided file does not appear to be a valid zip file.")
        sys.exit(1)


def get_artifacts_of_the_last_completed_run(headers, branch, run_id):
    response = requests.get(runs_url, headers=headers, params={'branch': branch})

    if response.status_code == 200:
        runs = response.json()['workflow_runs']

        completed_runs = list(run for run in runs if run['status'] == 'completed' and run['name'] == 'Test')
        if len(completed_runs) == 0:
            logging.error("No completed test runs found.")
            sys.exit(1)

        if run_id is None:
            latest_completed_workflow = max(completed_runs, key=lambda x: x['created_at'], default=None)
            artifacts_url = latest_completed_workflow['artifacts_url']
        else:
            filtered_runs = [run for run in runs if str(run['id']) == str(run_id)]
            if len(filtered_runs) == 0:
                logging.error("No run found with the specified ID.")
                sys.exit(1)
            elif len(filtered_runs) > 1:
                logging.error("Multiple runs found with the same ID. Using the first one.")
            artifacts_url = filtered_runs[0]['artifacts_url']

        response = requests.get(artifacts_url, headers=headers)
        artifacts = response.json()['artifacts']

        if not artifacts:
            logging.error("No artifacts found.")
            sys.exit(1)

        return artifacts
    elif response.status_code == 404:
        logging.error(f"Branch {branch} not found in the repository")
        sys.exit(1)
    else:
        logging.error(f"Error accessing Github with status code {response.status_code}")
        sys.exit(1)


def get_coverage_artifact_for_key(artifacts, key):
    matching_artifacts = list(artifact for artifact in artifacts if artifact['name'] == key)

    if len(matching_artifacts) == 1:
        return matching_artifacts[0]['archive_download_url']
    else:
        logging.error("Expected exactly one artifact, found {} for key: {}".format(len(matching_artifacts), key))
        return None


def get_branch_name():
    repo = git.Repo(repo_path)
    return repo.active_branch.name


def get_changed_files(branch_name, base_branch_name="origin/develop"):
    repo = git.Repo(repo_path)
    try:
        branch_head = repo.commit(branch_name)
    except Exception as e:
        if "did not resolve to an object" in str(e):
            logging.error(f"Branch {branch_name} does not exist")
            if "origin/" in branch_name:
                sys.exit(1)
            logging.info("Trying to fetch branch from remote")
            branch_name = f"origin/{branch_name}"
            branch_head = repo.commit(branch_name)

    branch_base = repo.merge_base(branch_name, base_branch_name)[0]
    diff_index = branch_head.diff(branch_base, create_patch=False, R=True)

    # File changes with change type
    file_changes = {}
    for diff in diff_index:
        file_path = diff.a_path if diff.a_path else diff.b_path
        file_changes[file_path] = {
            'A': 'added',
            'D': 'deleted',
            'M': 'modified',
            'R': 'renamed'
        }.get(diff.change_type, 'unknown')

    return file_changes


def filter_file_changes(file_changes):
    client_file_changes = {}
    server_file_changes = {}

    for file_name, change_type in file_changes.items():
        if file_name.startswith("src/main/webapp/app"):
            if file_name.endswith(".ts") and not file_name.endswith("module.ts"):
                client_file_changes[file_name[len("src/main/webapp/"):]] = change_type
                continue
        elif file_name.startswith("src/main/java/de/tum/in/www1/artemis"):
            if file_name.endswith(".java"):
                server_file_changes[file_name[len("src/main/java/"):]] = change_type
                continue
        logging.debug(f"Skipping {file_name}")
    return client_file_changes, server_file_changes


def get_client_line_coverage(client_coverage_zip_bytes, file_name, change_type):
    file_content = get_html_content(client_coverage_zip_bytes, file_name)

    line_coverage = None
    if file_content:
        logging.debug(f"Opening {file_content}")

        soup = BeautifulSoup(file_content, "html.parser")
        coverage_divs = soup.find_all("div", {"class": "fl pad1y space-right2"})
        if len(coverage_divs) >= 4:
            line_coverage_strong = coverage_divs[3].find("span", {"class": "strong"})
            line_coverage = line_coverage_strong.text.strip()
            logging.debug(f"Coverage for {file_name} -> line coverage: {line_coverage}")

    if line_coverage:
        return file_name, line_coverage
    else:
        return file_name, f"not found ({change_type})"


def get_server_line_coverage(server_coverage_zip_bytes, file_name, change_type):
    preliminary_path, class_name = file_name.replace(".java", "").rsplit("/", 1)
    package = preliminary_path.replace("/", ".")
    report_name = f"{package}/{class_name}"

    file_content = get_html_content(server_coverage_zip_bytes, report_name)
    line_coverage = None
    if file_content:
        logging.debug(f"Opening {report_name}")

        soup = BeautifulSoup(file_content, "html.parser")
        tfoot = soup.find("tfoot")
        if tfoot:
            ctr2_tds = tfoot.find_all("td", class_="ctr2")
            if ctr2_tds and len(ctr2_tds) > 0:
                line_coverage = ctr2_tds[0].text.strip()
        logging.debug(f"Coverage for {file_name} -> line coverage: {line_coverage}")

    if line_coverage:
        return file_name, line_coverage
    else:
        return file_name, f"not found ({change_type})"


def coverage_to_table(covs):
    if covs is None:
        return "Coverage artifact not found, tests probably failed."

    header = "| Class/File | Line Coverage | Confirmation (assert/expect) |\n|------------|--------------:|---------------------:|"
    table_data = []

    for cov in covs:
        class_file = cov[0].rsplit("/", 1)[1]
        line_coverage = cov[1]
        confirmation = "✅ ❌"
        if line_coverage.startswith("100"):
            confirmation = "✅"
        elif line_coverage.startswith("not found"):
            confirmation = "❌"
        table_data.append(f"| {class_file} | {line_coverage} | {confirmation} |")

    table = "\n".join([header] + table_data)
    return table


def main(argv):
    parser = argparse.ArgumentParser(
        description="Generate code coverage report for changed files in a Github Workflow build.",
        formatter_class=argparse.RawTextHelpFormatter,
    )
    parser.add_argument(
        "--token", help="Github Token with todo permissions(optional, will be prompted if not provided)",
        **environ_or_required("TOKEN", required=False)
    )
    parser.add_argument(
        "--branch-name", default=None,
        help="Name of the Git branch you want to compare with the base branch (default: origin/develop)"
    )
    parser.add_argument(
        "--base-branch-name", default="origin/develop", help="Name of the Git base branch (default: origin/develop)"
    )
    parser.add_argument(
        "--build-id", default=None, help="Build ID of the Github run id"
    )
    parser.add_argument(
        "--verbose", action="store_true", help="Enable verbose logging"
    )
    parser.add_argument(
        "--print-results", action="store_true",
        help="Print the report to console instead of copying to clipboard"
    )

    args = parser.parse_args(argv)
    if args.verbose:
        logging.getLogger().setLevel(logging.DEBUG)
    if args.token is None:
        args.token = getpass.getpass("Please enter your Github token: ")
    if args.branch_name is None:
        args.branch_name = get_branch_name()
        logging.info(f"Using current branch: {args.branch_name}")
    if args.build_id is None:
        logging.info("Using latest build ID")

    file_changes = get_changed_files(args.branch_name, args.base_branch_name)
    client_file_changes, server_file_changes = filter_file_changes(file_changes)

    headers = {
        'Authorization': f'token {args.token}',
        'Accept': 'application/vnd.github.v3+json'
    }

    artifacts = get_artifacts_of_the_last_completed_run(headers, args.branch_name, args.build_id)

    client_coverage_zip_bytes = download_and_extract_zip(get_coverage_artifact_for_key(artifacts, client_tests_key), headers, client_tests_key)
    server_coverage_zip_bytes = download_and_extract_zip(get_coverage_artifact_for_key(artifacts, server_tests_key), headers, server_tests_key)

    client_cov = [
        get_client_line_coverage(client_coverage_zip_bytes, file_name, change_type)
        for file_name, change_type in tqdm(client_file_changes.items(), desc="Building client coverage", unit="files")
    ] if client_coverage_zip_bytes is not None else None

    server_cov = [
        get_server_line_coverage(server_coverage_zip_bytes, file_name, change_type)
        for file_name, change_type in tqdm(server_file_changes.items(), desc="Building server coverage", unit="files")
    ] if server_coverage_zip_bytes is not None else None

    client_table = coverage_to_table(client_cov)
    server_table = coverage_to_table(server_cov)

    result = ""
    if client_file_changes:
        result += f"#### Client\n\n{client_table}\n\n"
    if server_file_changes:
        result += f"#### Server\n\n{server_table}\n\n"

    logging.info("Info: ✅ ❌ in Confirmation (assert/expect) have to be adjusted manually, also delete trivial files!")
    logging.info("")  # newline

    if args.print_results:
        print(result)
    else:
        result_utf16 = result.encode('utf-16le') + b'\x00\x00'
        pyperclip.copy(result_utf16.decode('utf-16le'))
        logging.info("Code coverage report copied to clipboard. Use --print-results to print it to console instead.")


if __name__ == "__main__":
    main(sys.argv[1:])
