import os
import sys
import zipfile
from logging_config import logging

from utils import CODE_SNAPSHOT_FILES, DATASET_VERSION
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


def create_code_snapshot(results_dir: str) -> str | None:
    """
    Creates a ZIP archive of the Hyperion Java source files relevant to the
    consistency check feature and stores it in the given results directory.

    The ZIP preserves the directory structure relative to the ``hyperion/`` folder,
    e.g.::

        hyperion/
            domain/ArtifactType.java
            dto/ConsistencyCheckResponseDTO.java
            service/HyperionConsistencyCheckService.java
            web/HyperionProblemStatementResource.java

    Which files to include is controlled by the ``code_snapshot_files`` setting
    in ``config.ini`` under ``[PECVConsistencyCheckSettings]``.

    :param str results_dir: The directory where the zip will be stored
        (e.g. ``pecv-bench/results/V2/{approach_id}/``).
    :return: The path to the created ZIP file, or ``None`` if creation failed.
    :rtype: str | None
    """
    hyperion_dir = get_hyperion_source_dir()

    if not os.path.isdir(hyperion_dir):
        logging.error(f"Hyperion source directory not found: {hyperion_dir}")
        return None

    zip_path = os.path.join(results_dir, "hyperion_consistency_check.zip")
    added_count = 0
    missing_files: list[str] = []

    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
        for subfolder, filenames in CODE_SNAPSHOT_FILES.items():
            for filename in filenames:
                src_path = os.path.join(hyperion_dir, subfolder, filename)
                # Archive path preserves the hyperion/ prefix
                arc_path = os.path.join("hyperion", subfolder, filename)

                if os.path.isfile(src_path):
                    zf.write(src_path, arcname=arc_path)
                    added_count += 1
                else:
                    missing_files.append(os.path.join(subfolder, filename))
                    logging.warning(f"Code snapshot: file not found — {src_path}")

    if missing_files:
        logging.warning(
            f"Code snapshot created with {added_count} files, "
            f"{len(missing_files)} missing: {', '.join(missing_files)}"
        )
    else:
        logging.info(f"Code snapshot created: {zip_path} ({added_count} files)")

    return zip_path


if __name__ == "__main__":
    # This file can be rerun standalone to create a code snapshot ZIP without regenerating reports.
    #
    # Steps to recover:
    #   1. Update approach_id below to match your results folder name.
    #      Find it with:  ls pecv-bench/results/<DATASET_VERSION>/
    #   2. Rerun:            python code_snapshot.py
    pecv_bench_dir = get_pecv_bench_dir()

    # >>> UPDATE THIS to your results folder name before rerunning <<<
    approach_id = "REPLACE_ME"

    if approach_id == "REPLACE_ME":
        logging.error(
            "approach_id is not set. Open code_snapshot.py and set it to your results folder name.\n"
            f"  Find it with:  ls {os.path.join(pecv_bench_dir, 'results')}/"
        )
        sys.exit(1)

    results_dir = os.path.join(pecv_bench_dir, "results", approach_id, DATASET_VERSION)
    create_code_snapshot(results_dir)
