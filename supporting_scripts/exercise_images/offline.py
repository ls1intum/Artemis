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
# "Could not resolve/GET" lines.
MISSING_ARTIFACT = re.compile(r"^(?:\[ERROR\]\s*)(.*(?:Could not transfer artifact|could not be resolved|Could not find artifact)[^\n]*)|^\s*(?:> )?(Could not (?:resolve|GET)[^\n]*)", re.MULTILINE)


@dataclass(frozen=True)
class Result:
    variant: Variant
    image: str
    passed: bool
    log: Path
    missing: list[str]


def build_script(phases: list[Phase]) -> str:
    """The script the build agent runs (``LocalCIBuildConfigurationService.computeBuildScript``)."""
    force_run = [phase for phase in phases if phase.force_run]
    regular = [phase for phase in phases if not phase.force_run]
    lines = ["#!/usr/bin/env bash", "set -e", f"cd {WORKING_DIRECTORY}", "export INITIAL_WORKING_DIRECTORY=${PWD}"]
    for phase in phases:
        script = phase.script.replace("${studentParentWorkingDirectoryName}", ASSIGNMENT_DIRECTORY)
        lines += [f"{phase.name} () {{", f"  echo '⚙️ executing {phase.name}'"]
        lines += [f"  {line}" if line else "" for line in script.splitlines()]
        lines += ["}", ""]
    if force_run:
        lines += ["final_force_run_post_action () {", "  set +e", "  echo '⚙️ executing final_force_run_post_action'"]
        lines += [f'  cd "${{INITIAL_WORKING_DIRECTORY}}"\n  {phase.name}' for phase in force_run]
        lines += ["}", ""]
    lines += ["main () {", '  if [[ "${1}" == "script_sourcing" ]]; then', "    return 0", "  fi", "  local _script_name", "  _script_name=${BASH_SOURCE[0]:-$0}"]
    if force_run:
        lines.append("  trap final_force_run_post_action EXIT")
    for phase in regular:
        lines += ['  cd "${INITIAL_WORKING_DIRECTORY}"', f'  bash -c "source ${{_script_name}} script_sourcing; {phase.name}"']
    lines += ["}", "", 'main "${@}"', ""]
    return "\n".join(lines)


def verify(templates_dir: Path, variant: Variant, image: str, work_dir: Path, platform: str | None = None) -> Result:
    """Assembles the repositories of ``variant`` and runs their build phases in ``image`` without a network."""
    repository = work_dir / variant.name / "testing-dir"
    assemble(templates_dir, variant, repository)
    script = work_dir / variant.name / "build.sh"
    script.write_text(build_script(load_phases(templates_dir, variant.language, variant.phases_file)), encoding="utf-8")
    log = work_dir / variant.name / "build.log"

    command = ["docker", "run", "--rm", "--network", "none"]
    if platform:
        command += ["--platform", platform]
    command += ["-v", f"{repository}:{WORKING_DIRECTORY}", "-v", f"{script}:/tmp/build.sh:ro", image, "bash", "/tmp/build.sh"]
    with log.open("w", encoding="utf-8") as output:
        completed = subprocess.run(command, stdout=output, stderr=subprocess.STDOUT, check=False)

    findings = MISSING_ARTIFACT.findall(log.read_text(encoding="utf-8", errors="replace"))
    missing = sorted({maven or gradle for maven, gradle in findings})
    return Result(variant, image, completed.returncode == 0, log, missing)
