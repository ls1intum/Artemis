import subprocess
import os
import sys
from logging_config import logging
from utils import REFERENCE, DATASET_VERSION
from exercises import get_pecv_bench_dir, install_pecv_bench_dependencies


def generate_report_files(pecv_bench_dir: str, version: str, approach_id: str) -> None:
    """
    Generate report and statistics for the benchmark results.

    :param str pecv_bench_dir: The root directory of the pecv-bench repository
    :param str version: The dataset version identifier
    :param str approach_id: The approach identifier
    :raises SystemExit: If any of the pecv-bench CLI commands fail
    """

    logging.info("Generating analysis and reports...")

    commands = [
        ["pecv-bench", "variants-analysis", "--results-dir", f"results/{approach_id}/{version}", "--clear"],
        ["pecv-bench", "variants-analysis", "--results-dir", f"results/{approach_id}/{version}", "--plot"],
        ["pecv-bench", "report", "--results-dir", f"results/{approach_id}/{version}"]
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
            logging.error(f"Step 13 failed: Command failed: {' '.join(cmd)}")
            logging.error(f"Stdout: {e.stdout.decode('utf-8')}")
            logging.error(f"Stderr: {e.stderr.decode('utf-8')}")
            logging.error(f"Open report.py, set approach_id = \"{approach_id}\", then execute Step 13 in report.py")
            logging.error("If 'pecv-bench' CLI is not found, execute Step 3 in exercises.py first, then Step 13")
            sys.exit(1)

    report_md_path = os.path.join(pecv_bench_dir, "results", approach_id, version, "report.md")
    summary_md_path = os.path.join(pecv_bench_dir, "results", approach_id, version, "summary.md")
    summarize_report(report_md_path, summary_md_path, version)


def summarize_report(report_md_path: str, summary_md_path: str, version: str) -> None:
    """
    Reads content from summary.md and inserts it into report.md
    immediately after the '# Variants Analysis Report' header.

    :param str report_md_path: The path to the report markdown file
    :param str summary_md_path: The path to the summary markdown file
    :param str version: The dataset version identifier for reference row lookup
    """
    if not os.path.exists(report_md_path):
        logging.error(f"Step 13 failed: Report file not found at {report_md_path}. Execute Step 13 in report.py")
        return

    if not os.path.exists(summary_md_path):
        logging.error(f"Step 13 failed: Summary file not found at {summary_md_path}. Execute Step 13 in report.py")
        return

    try:
        # read the summary text
        with open(summary_md_path, 'r', encoding='utf-8') as f:
            summary_text = f.readlines()

        new_summary_text = []
        inserted_reference = False
        reference_row = REFERENCE.get(version)
        if reference_row is None:
            logging.warning(f"No reference data found for version '{version}' in config.ini — 'No Data Available' will be shown in the report.")
            reference_row = "No Data Available"

        for line in summary_text:
            if not inserted_reference and line.startswith("| artemis-"):
                new_summary_text.append(line)
                new_summary_text.append(reference_row + "\n")
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
        logging.exception(f"Step 13 failed: Error while injecting summary into report: {e}. Execute Step 13 in report.py")


if __name__ == "__main__":
    # This file can be executed independently and after run_pecv_bench.py if it fails on the report step. Step 13
    #
    # Steps to recover:
    #   1. Update approach_id below to match your results folder name.
    #      Find it with:  ls pecv-bench/results/
    #      or from terminal output
    #   2. Execute Step 13: python3 report.py
    logging.info("Step 1: Getting PECV-Bench directories from config")
    pecv_bench_dir = get_pecv_bench_dir()
    logging.info("Step 3: installing pecv-bench dependencies")
    install_pecv_bench_dependencies(pecv_bench_dir)

    # >>> UPDATE THIS to your results folder name before rerunning <<<
    approach_id = "REPLACE_ME"

    if approach_id == "REPLACE_ME":
        logging.error(
            "approach_id is not set. Open report.py and set it to your results folder name.\n"
            f"  Find it with:  ls {os.path.join(pecv_bench_dir, 'results')}/"
        )
        sys.exit(1)

    logging.info("Step 13: Generating benchmark report")
    generate_report_files(pecv_bench_dir, DATASET_VERSION, approach_id)
