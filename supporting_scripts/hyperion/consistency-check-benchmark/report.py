import shutil
import subprocess
import os
import sys
from logging_config import logging
from utils import REFERENCE, DATASET_VERSION
from exercises import get_pecv_bench_dir

def generate_response_file(pecv_bench_dir: str, version: str, approach_id: str) -> None:
    """
    Generate report and statistics for the benchmark results.

    :param str pecv_bench_dir: The root directory of the pecv-bench repository
    :raises SystemExit: If any of the pecv-bench CLI commands fail
    """

    logging.info("Generating analysis and reports...")

    commands = [
        ["pecv-bench", "variants-analysis", "--results-dir", f"results/{version}/{approach_id}", "--clear"],
        ["pecv-bench", "variants-analysis", "--results-dir", f"results/{version}/{approach_id}", "--plot"],
        ["pecv-bench", "report", "--results-dir", f"results/{version}/{approach_id}"]
    ]

    for cmd in commands:
        try:
            logging.info(f"Running command: {' '.join(cmd)}")

            subprocess.run(
                cmd,
                cwd=pecv_bench_dir,
                check=True,
                capture_output=True
            )
        except subprocess.CalledProcessError as e:
            logging.error(f"Command failed: {' '.join(cmd)}")
            logging.error(f"Stdout: {e.stdout.decode('utf-8')}")
            logging.error(f"Stderr: {e.stderr.decode('utf-8')}")

            sys.exit(1)

    report_md_path = os.path.join(pecv_bench_dir, "results", version, approach_id, "report.md")
    summary_md_path = os.path.join(pecv_bench_dir, "results", version, approach_id, "summary.md")
    summarize_report(report_md_path, summary_md_path)

def summarize_report(report_md_path: str, summary_md_path: str) -> None:
    """
    Reads content from summary.md and inserts it into report.md
    immediately after the '# Variants Analysis Report' header.

    :param str report_md_path: The path to the report markdown file
    :param str summary_md_path: The path to the summary markdown file
    """
    if not os.path.exists(report_md_path):
        logging.error(f"Report file not found at {report_md_path}")
        return

    if not os.path.exists(summary_md_path):
        logging.error(f"Summary file not found at {summary_md_path}")
        return

    try:
        # read the summary text
        with open(summary_md_path, 'r', encoding='utf-8') as f:
            summary_text = f.readlines()

        new_summary_text = []
        inserted_reference = False
        for line in summary_text:
            if not inserted_reference and line.startswith("| artemis-"):
                new_summary_text.append(line)
                new_summary_text.append(REFERENCE + "\n")
                inserted_reference = True
                continue
            new_summary_text.append(line)

        # read the report lines
        with open(report_md_path, 'r', encoding='utf-8') as f:
            lines = f.readlines()

        new_lines = []
        inserted = False

        header_marker = "# Variants Analysis Report"

        for line in lines:
            new_lines.append(line)
            if not inserted and header_marker in line:
                new_lines.append("\n" + "".join(new_summary_text) + "\n")
                inserted = True

        # write back to report.md
        with open(report_md_path, 'w', encoding='utf-8') as f:
            f.writelines(new_lines)

        logging.info(f"Successfully injected summary from {os.path.basename(summary_md_path)} into {os.path.basename(report_md_path)}")

    except Exception as e:
        logging.exception(f"Error while injecting summary into report: {e}")

def check_gh_cli_installed() -> None:
    """
    Checks if the GitHub CLI (gh) is installed and authenticated.

    :raises SystemExit: if gh is not installed or not authenticated
    """
    if not shutil.which("gh"):
        logging.error(
            "GitHub CLI (gh) is not installed.\n"
            "  1. Install: brew install gh (macOS) or sudo apt install gh (Linux)\n"
            "  2. Authenticate: gh auth login\n"
            "  3. Rerun: python report.py"
        )
        sys.exit(1)

    try:
        subprocess.run(
            ["gh", "auth", "status"],
            check=True,
            capture_output=True,
            text=True,
        )
    except subprocess.CalledProcessError:
        logging.error(
            "GitHub CLI is not authenticated.\n"
            "  1. Run: gh auth login\n"
            "  2. Rerun: python report.py"
        )
        sys.exit(1)

    logging.info("GitHub CLI is installed and authenticated.")

def create_results_pull_request(pecv_bench_dir: str, approach_id: str) -> None:
    """
    Creates a branch in the pecv-bench repo, commits the results folder for the given
    approach_id, pushes it, and opens a pull request via GitHub CLI.

    The PR targets the 'dataset-extension' branch and uses report.md as the PR body.

    This function works on the current state of pecv-bench (which already has uncommitted
    results from the analysis). It creates a new branch from the current HEAD, stages only
    the results folder, commits, pushes, and opens a PR.

    Can be rerun standalone via: python report.py

    :param str pecv_bench_dir: The root directory of the pecv-bench repository
    :param str approach_id: The approach identifier (used as branch name and results folder name)
    """
    check_gh_cli_installed()

    results_dir = os.path.join(pecv_bench_dir, "results", DATASET_VERSION, approach_id)
    if not os.path.isdir(results_dir):
        logging.error(f"Results directory not found: {results_dir}")
        logging.error("Ensure the analysis has completed and results are generated before creating a PR.")
        sys.exit(1)

    report_md_path = os.path.join(results_dir, "report.md")
    if os.path.exists(report_md_path):
        with open(report_md_path, "r", encoding="utf-8") as f:
            pr_body = f.read()
    else:
        logging.warning(f"report.md not found at {report_md_path}. Using default PR body.")
        pr_body = f"Automated results upload for {approach_id}"

    branch_name = approach_id

    try:
        # Create a new branch from current HEAD (already on dataset-extension with results)
        subprocess.run(
            ["git", "checkout", "-b", branch_name],
            cwd=pecv_bench_dir,
            check=True,
            capture_output=True,
            text=True,
        )
        logging.info(f"Created branch '{branch_name}' in pecv-bench.")

        # Stage only the results folder for this approach
        subprocess.run(
            ["git", "add", os.path.join("results", DATASET_VERSION, approach_id)],
            cwd=pecv_bench_dir,
            check=True,
            capture_output=True,
            text=True,
        )

        # Commit
        subprocess.run(
            ["git", "commit", "-m", f"Add results for {approach_id}"],
            cwd=pecv_bench_dir,
            check=True,
            capture_output=True,
            text=True,
        )
        logging.info(f"Committed results for {approach_id}.")

        # Push
        subprocess.run(
            ["git", "push", "-u", "origin", branch_name],
            cwd=pecv_bench_dir,
            check=True,
            capture_output=True,
            text=True,
        )
        logging.info(f"Pushed branch '{branch_name}' to origin.")

        # Create PR targeting dataset-extension
        result = subprocess.run(
            [
                "gh", "pr", "create",
                "--title", branch_name,
                "--body", pr_body,
                "--base", "dataset-extension",
                # "--base", "main",
            ],
            cwd=pecv_bench_dir,
            check=True,
            capture_output=True,
            text=True,
        )
        logging.info(f"Pull request created: {result.stdout.strip()}")

    except subprocess.CalledProcessError as e:
        logging.error(f"Failed to create pull request: {e}")
        if e.stdout:
            logging.error(f"Stdout: {e.stdout}")
        if e.stderr:
            logging.error(f"Stderr: {e.stderr}")
        sys.exit(1)

if __name__ == "__main__":
    # This file can be rerun standalone after run_pecv_bench.py fails on the PR step.
    # Steps to recover:
    #   1. Install gh:  brew install gh
    #   2. Auth gh:     gh auth login
    #   3. Rerun:       python report.py
    pecv_bench_dir = get_pecv_bench_dir()

    # Set this to the results folder name (ls pecv-bench/results/{DATASET_VERSION}/ to find it)
    approach_id = "artemis-feature-hyperion-extend_run_pecv_bench_scripts"

    create_results_pull_request(pecv_bench_dir, approach_id)