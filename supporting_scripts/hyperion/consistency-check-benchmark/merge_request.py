import re
import shutil
import subprocess
import os
import sys
from logging_config import logging

from utils import PECV_BENCH_BRANCH, DATASET_VERSION
from exercises import get_pecv_bench_dir


def _stage_results_and_runs(pecv_bench_dir: str, approach_id: str) -> None:
    """
    Stage the results and (if present) runs directories for a given approach.

    :param str pecv_bench_dir: The root directory of the pecv-bench repository.
    :param str approach_id: The approach identifier used as the folder name.
    :rtype: None
    """
    subprocess.run(
        ["git", "add", os.path.join("results", approach_id)],
        cwd=pecv_bench_dir,
        check=True,
        capture_output=True,
        text=True,
    )
    runs_dir = os.path.join("runs", approach_id)
    if os.path.isdir(os.path.join(pecv_bench_dir, runs_dir)):
        subprocess.run(
            ["git", "add", runs_dir],
            cwd=pecv_bench_dir,
            check=True,
            capture_output=True,
            text=True,
        )


def check_gh_cli_installed() -> None:
    """
    Checks if the GitHub CLI (gh) is installed and authenticated.

    :raises SystemExit: if gh is not installed or not authenticated.
    :rtype: None
    """
    if not shutil.which("gh"):
        logging.error(
            "Step 14 failed: GitHub CLI (gh) is not installed.\n"
            "  1. Install: brew install gh (macOS) or sudo apt install gh (Linux)\n"
            "  2. Authenticate: gh auth login\n"
            "  3. Execute Step 14 in merge_request.py"
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
            "Step 14 failed: GitHub CLI is not authenticated.\n"
            "  1. Run: gh auth login\n"
            "  2. Execute Step 14 in merge_request.py"
        )
        sys.exit(1)

    logging.info("GitHub CLI is installed and authenticated.")


def create_results_pull_request(pecv_bench_dir: str, approach_id: str) -> None:
    """
    Creates a branch in the pecv-bench repo, commits the results folder for the given
    approach_id, pushes it, and opens a pull request via GitHub CLI.

    The PR targets the branch configured via ``pecv_bench_branch`` in config.ini and uses report.md as the PR body.

    This function works on the current state of pecv-bench (which already has uncommitted
    results from the analysis). It creates a new branch from the current HEAD, stages only
    the results folder, commits, pushes, and opens a PR.

    Can be rerun standalone via: python merge_request.py

    :param str pecv_bench_dir: The root directory of the pecv-bench repository.
    :param str approach_id: The approach identifier (used as branch name and results folder name).
    :raises SystemExit: if results are missing, or if any git/gh CLI command fails.
    :rtype: None
    """
    check_gh_cli_installed()

    approach_results_dir_check = os.path.join(pecv_bench_dir, "results", approach_id)
    if not os.path.isdir(approach_results_dir_check) or not any(
        os.path.isdir(os.path.join(approach_results_dir_check, v))
        for v in os.listdir(approach_results_dir_check)
    ):
        logging.error(f"Step 14 failed: No version results found for approach '{approach_id}' in: {approach_results_dir_check}")
        logging.error("Ensure the analysis has completed and results are generated before creating a PR. Execute Step 14 in merge_request.py")
        sys.exit(1)

    branch_name = approach_id

    # Collect version directories for this approach (used for staging and PR body)
    results_root = os.path.join(pecv_bench_dir, "results")
    approach_results_dir = os.path.join(results_root, approach_id)
    all_versions = sorted(
        v for v in os.listdir(approach_results_dir)
        if os.path.isdir(os.path.join(approach_results_dir, v))
    ) if os.path.isdir(approach_results_dir) else [DATASET_VERSION]

    remote_url = subprocess.run(
        ["git", "remote", "get-url", "origin"],
        cwd=pecv_bench_dir,
        capture_output=True,
        text=True,
    ).stdout.strip()
    repo_slug_match = re.search(r"github\.com[:/](.+?)(?:\.git)?$", remote_url)

    try:
        # Stage results and runs folders BEFORE switching branches so git doesn't
        # refuse to checkout due to "untracked files would be overwritten".
        _stage_results_and_runs(pecv_bench_dir, approach_id)

        # Check if the remote branch already exists (e.g. from a previous version's run)
        remote_check = subprocess.run(
            ["git", "ls-remote", "--heads", "origin", branch_name],
            cwd=pecv_bench_dir,
            capture_output=True,
            text=True,
        )
        remote_exists = bool(remote_check.stdout.strip())

        if remote_exists:
            # Fetch and base the local branch on the remote so previous results
            # (e.g. V1) are preserved and we only add new ones on top.
            subprocess.run(
                ["git", "fetch", "origin", branch_name],
                cwd=pecv_bench_dir,
                check=True,
                capture_output=True,
                text=True,
            )
            subprocess.run(
                ["git", "checkout", "-B", branch_name, f"origin/{branch_name}"],
                cwd=pecv_bench_dir,
                check=True,
                capture_output=True,
                text=True,
            )
            logging.info(f"Checked out existing remote branch '{branch_name}'.")
            # Re-stage results after checkout (checkout cleared the index)
            _stage_results_and_runs(pecv_bench_dir, approach_id)
        else:
            # Base the new branch on origin/{PECV_BENCH_BRANCH} so the PR diff
            # only shows this approach's results, not other locally committed approaches.
            subprocess.run(
                ["git", "fetch", "origin", PECV_BENCH_BRANCH],
                cwd=pecv_bench_dir,
                check=True,
                capture_output=True,
                text=True,
            )
            subprocess.run(
                ["git", "checkout", "-b", branch_name, f"origin/{PECV_BENCH_BRANCH}"],
                cwd=pecv_bench_dir,
                check=True,
                capture_output=True,
                text=True,
            )
            logging.info(f"Created branch '{branch_name}' in pecv-bench.")
            # Re-stage results after checkout (checkout cleared the index)
            _stage_results_and_runs(pecv_bench_dir, approach_id)

        # Commit (skip if nothing new to commit)
        status = subprocess.run(
            ["git", "diff", "--cached", "--quiet"],
            cwd=pecv_bench_dir,
            capture_output=True,
        )
        if status.returncode != 0:
            subprocess.run(
                ["git", "commit", "-m", f"Add results for {approach_id}"],
                cwd=pecv_bench_dir,
                check=True,
                capture_output=True,
                text=True,
            )
            logging.info(f"Committed results for {approach_id}.")
        else:
            logging.info("No new changes to commit — results already committed.")

        # Push — normal fast-forward since we built on top of the remote branch
        subprocess.run(
            ["git", "push", "-u", "origin", branch_name],
            cwd=pecv_bench_dir,
            check=True,
            capture_output=True,
            text=True,
        )
        logging.info(f"Pushed branch '{branch_name}' to origin.")

        # Build PR body AFTER push so raw GitHub image URLs resolve correctly
        combined_parts: list[str] = []
        for version in all_versions:
            report_md_path = os.path.join(results_root, approach_id, version, "report.md")
            if not os.path.exists(report_md_path):
                logging.warning(f"report.md not found for version {version}, skipping.")
                continue
            with open(report_md_path, "r", encoding="utf-8") as f:
                section = f.read()
            # Replace relative image paths with absolute raw GitHub URLs
            if repo_slug_match:
                repo_slug = repo_slug_match.group(1)
                raw_base = f"https://raw.githubusercontent.com/{repo_slug}/{branch_name}/results/{approach_id}/{version}"
                section = re.sub(
                    r"!\[([^\]]*)\]\((?!https?://)([^)]+)\)",
                    lambda m, base=raw_base: f"![{m.group(1)}]({base}/{m.group(2).lstrip('./')})",
                    section,
                )
            combined_parts.append(f"## {version}\n\n{section}")

        if combined_parts:
            pr_body = f"# {approach_id}\n\n" + "\n\n---\n\n".join(combined_parts)
        else:
            logging.warning("No report.md files found. Using default PR body.")
            pr_body = f"# {approach_id}\n\nAutomated results upload."

        # Create PR, or update body if one already exists for this branch
        pr_check = subprocess.run(
            ["gh", "pr", "view", branch_name, "--json", "url", "--jq", ".url"],
            cwd=pecv_bench_dir,
            capture_output=True,
            text=True,
        )
        if pr_check.returncode == 0 and pr_check.stdout.strip():
            existing_url = pr_check.stdout.strip()
            subprocess.run(
                ["gh", "pr", "edit", branch_name, "--body", pr_body],
                cwd=pecv_bench_dir,
                check=True,
                capture_output=True,
                text=True,
            )
            logging.info(f"Pull request updated: {existing_url}")
        else:
            result = subprocess.run(
                [
                    "gh", "pr", "create",
                    "--title", branch_name,
                    "--body", pr_body,
                    "--base", PECV_BENCH_BRANCH,
                ],
                cwd=pecv_bench_dir,
                check=True,
                capture_output=True,
                text=True,
            )
            logging.info(f"Pull request created: {result.stdout.strip()}")

    except subprocess.CalledProcessError as e:
        logging.error(f"Step 14 failed: Failed to create pull request: {e}")
        if e.stdout:
            logging.error(f"Stdout: {e.stdout}")
        if e.stderr:
            logging.error(f"Stderr: {e.stderr}")
        logging.error(f"Install gh (brew install gh), authenticate (gh auth login), open merge_request.py, set approach_id = \"{approach_id}\", then execute Step 14 in merge_request.py")
        sys.exit(1)


if __name__ == "__main__":
    # This file can be executed independently and after run_pecv_bench.py if it fails on the PR step. Step 14
    # Steps to recover:
    #   1. Install gh:       brew install gh
    #   2. Authenticate gh:  gh auth login
    #   3. Update approach_id below to match your results folder name.
    #      Find it with:  ls pecv-bench/results/
    #      or from terminal output
    #   4. Execute Step 14 in merge_request.py
    logging.info("Step 1: Getting PECV-Bench directories from config")
    pecv_bench_dir = get_pecv_bench_dir()

    # >>> UPDATE THIS to your results folder name before rerunning <<<
    approach_id = "REPLACE_ME"

    if approach_id == "REPLACE_ME":
        logging.error(
            "approach_id is not set. Open merge_request.py and set it to your results folder name.\n"
            f"  Find it with:  ls {os.path.join(pecv_bench_dir, 'results')}/"
        )
        sys.exit(1)

    logging.info("Step 14: Creating pull request with results in pecv-bench")
    create_results_pull_request(pecv_bench_dir, approach_id)
