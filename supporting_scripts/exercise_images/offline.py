"""Builds assembled exercise repositories in their image without network access.

The images exist so that exercise builds download nothing. A container without a network turns every dependency the
image does not hold into a failed build, instead of a silent download from Maven Central.
"""

import re
import subprocess
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

from .assembly import ASSIGNMENT_DIRECTORY, TEST_DIRECTORY, Variant, assemble
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


def checkout_directories(text: str) -> str:
    """The checkout directories of a build whose exercise sets none (``BuildScriptProviderService.replacePlaceholders``)."""
    return text.replace("${studentParentWorkingDirectoryName}", ASSIGNMENT_DIRECTORY).replace("${testWorkingDirectory}", TEST_DIRECTORY)


def read_report(report: Path) -> tuple[int, int] | None:
    """The number of test cases and of failed ones (a ``failure`` or ``error`` element) of a complete JUnit file, or ``None`` for anything else.

    A complete file has ``testsuite`` or ``testsuites`` as its root. A truncated or otherwise malformed file is none, and so is one with a
    DOCTYPE or entity declaration: the standard library parser expands entities, and a test report has no use for them.
    """
    text = report.read_text(encoding="utf-8", errors="replace")
    if "<!DOCTYPE" in text or "<!ENTITY" in text:
        return None
    try:
        root = ET.fromstring(text.lstrip("\ufeff"))  # noqa: S314 - no DOCTYPE or entity declaration, checked above
    except ET.ParseError:
        return None
    if root.tag.rpartition("}")[2] not in ("testsuite", "testsuites"):
        return None
    cases = [element for element in root.iter() if element.tag.rpartition("}")[2] == "testcase"]
    failed = [case for case in cases if any(child.tag.rpartition("}")[2] in ("failure", "error") for child in case)]
    return len(cases), len(failed)


def missing_reports(repository: Path, phases: list[Phase]) -> list[str]:
    """The phases that were meant to leave a test report (JUnit XML) and left none with a test case, or one with a failed test case.

    The test phases of several languages hide the exit status of the test run (``|| true``, a pipe through a converter), so a run that could
    not even start, for example because a library of the tests is missing in the image, still ends with exit status 0. Its report is the
    evidence, and so is a failed test case in it (the tests of the C templates record a sanitizer that cannot link as a failure). The
    templates are built from their solution, so every test has to pass. Static code analysis phases (``forceRun``) and result files of
    other formats are not looked at.

    The reports are looked at after the whole build, so a phase whose result paths another phase also writes (the structural and the
    behavior stage of the sequential Java and Kotlin builds share ``**/target/surefire-reports/*.xml``) can be satisfied by the report of
    the other one. Those builds do not hide the exit status of Maven or Gradle, and every other sequential build has a result path per
    stage, so the verdict only misses a stage that exits with 0 and writes nothing next to a stage that wrote a report.
    """
    problems = []
    for phase in phases:
        patterns = [checkout_directories(path) for path in phase.result_paths if path.endswith(".xml")]
        if phase.force_run or not patterns:
            continue
        reports = {report: read_report(report) for pattern in patterns for report in repository.glob(pattern) if report.is_file()}
        if not any(counts and counts[0] for counts in reports.values()):
            problems.append(f"The phase {phase.name} left no test report with a test case ({', '.join(patterns)})")
        # Every report counts, a failed test in one of them is not made up for by passing tests in another
        for report, counts in sorted(reports.items()):
            if counts and counts[1]:
                problems.append(f"The phase {phase.name} left a test report with {counts[1]} failed of {counts[0]} test cases ({report.relative_to(repository).as_posix()})")
    return problems


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
        script = checkout_directories(phase.script)
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


def make_writable(repository: Path) -> None:
    """Lets every user of the container change the mounted repository.

    The build agent copies the repositories into the container, where the build user owns them. A bind mount keeps the owner of the host
    (the user of the runner), so the build user of the image (`artemis_user`) could not delete or replace a file of the tests, which phases
    of C do, and the build would fail for a reason that production does not have.
    """
    for path in [repository, *repository.rglob("*")]:
        if not path.is_symlink():
            path.chmod(path.stat().st_mode | (0o777 if path.is_dir() else 0o666))


def docker_command(repository: Path, script: Path, image: str, platform: str | None = None) -> list[str]:
    """The ``docker run`` that builds ``repository`` in ``image`` without a network.

    The mount sources are made absolute: Docker reads a relative source such as ``results/java/testing-dir`` as the name of a volume.
    """
    command = ["docker", "run", "--rm", "--network", "none"]
    # pub retries a request that fails with exponential backoff, which took about 20 minutes per build without a network. A package the image
    # does not hold still fails at once, a cached one needs no request.
    command += ["-e", "PUB_MAX_HTTP_RETRIES=0"]
    if platform:
        command += ["--platform", platform]
    # The destination paths are inside the container
    command += ["-v", f"{repository.resolve()}:{WORKING_DIRECTORY}", "-v", f"{script.resolve()}:/tmp/build.sh:ro", image, "bash", "/tmp/build.sh"]
    return command


def verify(templates_dir: Path, variant: Variant, image: str, work_dir: Path, platform: str | None = None) -> Result:
    """Assembles the repositories of ``variant`` and runs their build phases in ``image`` without a network."""
    repository = work_dir / variant.name / "testing-dir"
    assemble(templates_dir, variant, repository)
    make_writable(repository)
    script = work_dir / variant.name / "build.sh"
    phases = load_phases(templates_dir, variant.language, variant.phases_file)
    script.write_text(build_script(phases), encoding="utf-8")
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
        make_writable(repository)

    missing = sorted({next(finding for finding in findings if finding) for findings in MISSING_ARTIFACT.findall(output)})
    if completed.returncode != 0 and not missing:
        missing = [line for line in output.splitlines() if line.strip()][-LOG_TAIL_LINES:]
    # A build that exited with 0 is only a success when its test phases left their reports (see `missing_reports`)
    reports = missing_reports(repository, phases) if completed.returncode == 0 else []
    return Result(variant, image, completed.returncode == 0 and not reports, log, missing + reports, attempts)
