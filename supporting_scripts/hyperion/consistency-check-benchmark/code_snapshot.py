import os
import sys
import zipfile
from logging_config import logging

from utils import CODE_SNAPSHOT_FILES
from exercises import get_pecv_bench_dir


def get_hyperion_source_dir() -> str:
    """
    Returns the absolute path to the Hyperion Java source directory.

    The benchmark scripts live in:
        ``<repo>/supporting_scripts/hyperion/consistency-check-benchmark/``

    The Java sources live in:
        ``<repo>/src/main/java/de/tum/cit/aet/artemis/hyperion/``

    :return: Absolute path to the hyperion source directory.
    :rtype: str
    """
    script_dir = os.path.dirname(os.path.abspath(__file__))
    # Navigate up from consistency-check-benchmark -> hyperion -> supporting_scripts -> repo root
    repo_root = os.path.normpath(os.path.join(script_dir, "..", "..", ".."))
    hyperion_dir = os.path.join(repo_root, "src", "main", "java", "de", "tum", "cit", "aet", "artemis", "hyperion")
    return hyperion_dir


def get_hyperion_prompts_dir() -> str:
    """
    Returns the absolute path to the Hyperion prompt templates directory.

    The benchmark scripts live in:
        ``<repo>/supporting_scripts/hyperion/consistency-check-benchmark/``

    The prompt templates live in:
        ``<repo>/src/main/resources/prompts/hyperion/``

    :return: Absolute path to the hyperion prompts directory.
    :rtype: str
    """
    script_dir = os.path.dirname(os.path.abspath(__file__))
    # Navigate up from consistency-check-benchmark -> hyperion -> supporting_scripts -> repo root
    repo_root = os.path.normpath(os.path.join(script_dir, "..", "..", ".."))
    prompts_dir = os.path.join(repo_root, "src", "main", "resources", "prompts", "hyperion")
    return prompts_dir


def create_code_snapshot(approach_results_dir: str, approach_id: str) -> str | None:
    """
    Creates a ZIP archive of the Hyperion Java source files and prompt templates
    relevant to the consistency check feature and stores it in the approach results directory.

    The ZIP preserves the directory structure relative to the ``hyperion/`` folder,
    e.g.::

        hyperion/
            domain/ArtifactType.java
            dto/ConsistencyCheckResponseDTO.java
            service/HyperionConsistencyCheckService.java
            web/HyperionProblemStatementResource.java
            prompts/consistency_check.txt
            prompts/consistency_system.txt

    Java source files to include are controlled by the ``code_snapshot_files`` setting
    in ``config.ini`` under ``[PECVConsistencyCheckSettings]``.

    Prompt files are taken from ``src/main/resources/prompts/hyperion/`` and filtered
    to only those whose filename starts with ``consistency_``.

    :param str approach_results_dir: The approach-level directory where the zip will be stored
        (e.g. ``pecv-bench/results/{approach_id}/``).
    :param str approach_id: The approach identifier, used as the ZIP filename.
    :return: The path to the created ZIP file, or ``None`` if creation failed.
    :rtype: str | None
    """
    hyperion_dir = get_hyperion_source_dir()
    prompts_dir = get_hyperion_prompts_dir()

    if not os.path.isdir(hyperion_dir):
        logging.error(f"Step 13b failed: Hyperion source directory not found: {hyperion_dir}. Execute Step 13b in code_snapshot.py")
        return None

    os.makedirs(approach_results_dir, exist_ok=True)

    zip_path = os.path.join(approach_results_dir, f"{approach_id}.zip")
    added_count = 0
    missing_files: list[str] = []

    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
        # Java source files
        for subfolder, filenames in CODE_SNAPSHOT_FILES.items():
            for filename in filenames:
                src_path = os.path.join(hyperion_dir, subfolder, filename)
                arc_path = os.path.join("hyperion", subfolder, filename)

                if os.path.isfile(src_path):
                    zf.write(src_path, arcname=arc_path)
                    added_count += 1
                else:
                    missing_files.append(os.path.join(subfolder, filename))
                    logging.warning(f"Code snapshot: file not found — {src_path}")

        # Prompt templates: only files starting with "consistency_"
        if os.path.isdir(prompts_dir):
            for filename in sorted(os.listdir(prompts_dir)):
                if filename.startswith("consistency_"):
                    src_path = os.path.join(prompts_dir, filename)
                    if os.path.isfile(src_path):
                        arc_path = os.path.join("hyperion", "prompts", filename)
                        zf.write(src_path, arcname=arc_path)
                        added_count += 1
        else:
            logging.warning(f"Code snapshot: prompts directory not found — {prompts_dir}")

    if missing_files:
        logging.warning(
            f"Code snapshot created with {added_count} files, "
            f"{len(missing_files)} missing: {', '.join(missing_files)}"
        )
    else:
        logging.info(f"Code snapshot created: {zip_path} ({added_count} files)")

    return zip_path


if __name__ == "__main__":
    # This file can be executed independently and after run_pecv_bench.py if it fails on snapshot ZIP step. Step 13b
    #
    # Steps to recover:
    #   1. Update approach_id below to match your results folder name.
    #      Find it with:  ls pecv-bench/results/
    #      or from terminal output
    #   2. Execute Step 13b in code_snapshot.py
    logging.info("Step 1: Getting PECV-Bench directories from config")
    pecv_bench_dir = get_pecv_bench_dir()

    # >>> UPDATE THIS to your results folder name before rerunning <<<
    approach_id = "REPLACE_ME"

    if approach_id == "REPLACE_ME":
        logging.error(
            "approach_id is not set. Open code_snapshot.py and set it to your results folder name.\n"
            f"  Find it with:  ls {os.path.join(pecv_bench_dir, 'results')}/"
        )
        sys.exit(1)

    logging.info("Step 13b: Creating code snapshot ZIP")
    approach_results_dir = os.path.join(pecv_bench_dir, "results", approach_id)
    create_code_snapshot(approach_results_dir, approach_id)
