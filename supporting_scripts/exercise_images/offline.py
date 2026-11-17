"""Builds assembled exercise repositories in their image without network access.

The images exist so that exercise builds download nothing. A container without a network turns every dependency the
image does not hold into a failed build, instead of a silent download from Maven Central.
"""

import re
import subprocess
from dataclasses import dataclass
from pathlib import Path

from .assembly import ASSIGNMENT_DIRECTORY, Variant, assemble
from .phases import Phase, load_phases

WORKING_DIRECTORY = "/var/tmp/testing-dir"
# Maven reports what it could not get as [ERROR] lines (the same lookups in [WARNING] lines are only metadata refreshes), Gradle as
# "Could not resolve/GET" lines, cargo as "error: failed to get/download" lines. Other tools (npm, SwiftPM) have no pattern; the log
# tail of a failed build is reported instead.
MISSING_ARTIFACT = re.compile(
    r"^(?:\[ERROR\]\s*)(.*(?:Could not transfer artifact|could not be resolved|Could not find artifact)[^\n]*)"
    r"|^\s*(?:> )?(Could not (?:resolve|GET)[^\n]*)"
    r"|^error: (failed to (?:get|download)[^\n]*)",
    re.MULTILINE,
)
LOG_TAIL_LINES = 5
# The Swift compiler sometimes fails with this error when it compiles in parallel, and succeeds with the same inputs on the next try.
# It has nothing to do with a missing dependency, so a build that failed only with it is run once more.
KNOWN_FLAKY_FAILURE = re.compile(r"error: module '\w+' is defined in both")


@dataclass(frozen=True)
class Result:
    variant: Variant
    image: str
    passed: bool
    log: Path
    missing: list[str]
    attempts: int = 1


def build_script(phases: list[Phase]) -> str:
    """The script the build agent runs (``LocalCIBuildConfigurationService.computeBuildScript``), with one difference.

    The build agent ignores a failing ``forceRun`` phase (static code analysis) and reads its reports instead. Here a failing
    ``forceRun`` phase fails the script too: a dependency that only the static code analysis needs is as missing as any other.
    An earlier failure of a regular phase keeps its exit status.
    """
    force_run = [phase for phase in phases if phase.force_run]
    regular = [phase for phase in phases if not phase.force_run]
    lines = ["#!/usr/bin/env bash", "set -e", f"cd {WORKING_DIRECTORY}", "export INITIAL_WORKING_DIRECTORY=${PWD}"]
    for phase in phases:
        script = phase.script.replace("${studentParentWorkingDirectoryName}", ASSIGNMENT_DIRECTORY)
        lines += [f"{phase.name} () {{", f"  echo '⚙️ executing {phase.name}'"]
        lines += [f"  {line}" if line else "" for line in script.splitlines()]
        lines += ["}", ""]
    if force_run:
        # Each phase runs in its own shell, like the regular ones, so that `set -e` applies to all of its commands. A phase that is
        # tested directly (`phase || ...`) would run without it, and a failing command followed by a succeeding one would go unnoticed.
        lines += ["final_force_run_post_action () {", "  local _exit_status=$?", "  local _phase_status", "  set +e", "  echo '⚙️ executing final_force_run_post_action'"]
        for phase in force_run:
            lines += [
                '  cd "${INITIAL_WORKING_DIRECTORY}"',
                "  _phase_status=0",
                f'  bash -c "source ${{0}} script_sourcing; {phase.name}" || _phase_status=$?',
                '  if [ "${_exit_status}" -eq 0 ]; then _exit_status=${_phase_status}; fi',
            ]
        lines += ['  exit "${_exit_status}"', "}", ""]
    lines += ["main () {", '  if [[ "${1}" == "script_sourcing" ]]; then', "    return 0", "  fi", "  local _script_name", "  _script_name=${BASH_SOURCE[0]:-$0}"]
    if force_run:
        lines.append("  trap final_force_run_post_action EXIT")
    for phase in regular:
        lines += ['  cd "${INITIAL_WORKING_DIRECTORY}"', f'  bash -c "source ${{_script_name}} script_sourcing; {phase.name}"']
    lines += ["}", "", 'main "${@}"', ""]
    return "\n".join(lines)


def docker_command(repository: Path, script: Path, image: str, platform: str | None = None) -> list[str]:
    """The ``docker run`` that builds ``repository`` in ``image`` without a network.

    The mount sources are made absolute: Docker reads a relative source such as ``results/java/testing-dir`` as the name of a volume.
    """
    command = ["docker", "run", "--rm", "--network", "none"]
    if platform:
        command += ["--platform", platform]
    # The destination paths are inside the container
    command += ["-v", f"{repository.resolve()}:{WORKING_DIRECTORY}", "-v", f"{script.resolve()}:/tmp/build.sh:ro", image, "bash", "/tmp/build.sh"]
    return command


def verify(templates_dir: Path, variant: Variant, image: str, work_dir: Path, platform: str | None = None) -> Result:
    """Assembles the repositories of ``variant`` and runs their build phases in ``image`` without a network."""
    repository = work_dir / variant.name / "testing-dir"
    assemble(templates_dir, variant, repository)
    script = work_dir / variant.name / "build.sh"
    script.write_text(build_script(load_phases(templates_dir, variant.language, variant.phases_file)), encoding="utf-8")
    log = work_dir / variant.name / "build.log"

    attempts = 0
    while True:
        attempts += 1
        with log.open("w", encoding="utf-8") as output:
            completed = subprocess.run(docker_command(repository, script, image, platform), stdout=output, stderr=subprocess.STDOUT, check=False)
        output = log.read_text(encoding="utf-8", errors="replace")
        if completed.returncode == 0 or attempts > 1 or not KNOWN_FLAKY_FAILURE.search(output):
            break
        # The failed attempt left build products behind, start from the assembled repository again
        assemble(templates_dir, variant, repository)

    missing = sorted({next(finding for finding in findings if finding) for findings in MISSING_ARTIFACT.findall(output)})
    if completed.returncode != 0 and not missing:
        missing = [line for line in output.splitlines() if line.strip()][-LOG_TAIL_LINES:]
    return Result(variant, image, completed.returncode == 0, log, missing, attempts)
