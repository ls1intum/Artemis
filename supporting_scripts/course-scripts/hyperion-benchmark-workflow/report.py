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
        ["pecv-bench", "variants-analysis", "--results-dir", f"results/{version}/{approach_id}", "--plot", f"--{version}"],
        ["pecv-bench", "report", "--benchmark", f"{approach_id}", f"--{version}"]
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
            if not inserted_reference and line.startswith("| artemis-benchmark "):
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

if __name__ == "__main__":
    pecv_bench_dir = get_pecv_bench_dir()
    #for testing purposes
    approach_id = "artemis-feature-hyperion-run_pecv_bench_in_artemis"

    generate_response_file(pecv_bench_dir, DATASET_VERSION, approach_id)