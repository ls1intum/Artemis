#!/usr/bin/env bats
# Unit tests for resolve-artifact-run.sh with a mocked `gh` on PATH.

setup() {
  SCRIPT="${BATS_TEST_DIRNAME}/../resolve-artifact-run.sh"
  BIN="$(mktemp -d)"
  cat > "${BIN}/gh" <<'EOF'
#!/usr/bin/env bash
# Fake `gh api`: emulate the already-`--jq`'d output for the two endpoints the script calls.
#   RUN_IDS      space-separated run ids (as GitHub returns them: newest first)
#   ART_PRESENT  space-separated run ids that carry a non-expired artifact
endpoint=""; skip=0
for a in "$@"; do
  if [ "${skip}" = 1 ]; then skip=0; continue; fi
  case "${a}" in
    api) continue ;;
    -f|-F|-H|-X|--jq) skip=1; continue ;;
    -*) continue ;;
    *) endpoint="${a}"; break ;;
  esac
done
case "${endpoint}" in
  */actions/workflows/*/runs)
    printf '%s\n' ${RUN_IDS} ;;
  */actions/runs/*/artifacts)
    id="${endpoint#*/actions/runs/}"; id="${id%/artifacts}"
    for good in ${ART_PRESENT}; do
      if [ "${id}" = "${good}" ]; then echo 1; exit 0; fi
    done
    echo 0 ;;
esac
EOF
  chmod +x "${BIN}/gh"
  PATH="${BIN}:${PATH}"
  OUT="$(mktemp)"
}

teardown() { rm -rf "${BIN}" "${OUT}"; }

SHA=da39a3ee5e6b4b0d3255bfef95601890afd80709

@test "resolves the run that carries the artifact, skipping the newest run that does not" {
  # Newest-first: run 222 (superseded, no artifact) sorts ahead of run 111 (has it). The old
  # `.workflow_runs[0]` logic would pick 222 and fail; this must pick 111.
  export RUN_IDS="222 111"
  export ART_PRESENT="111"
  run env REPO=o/r WORKFLOW=ci.yml SHA="${SHA}" ARTIFACT_NAME=Artemis.war GITHUB_OUTPUT="${OUT}" "${SCRIPT}"
  [ "${status}" -eq 0 ]
  grep -q '^run_id=111$' "${OUT}"
}

@test "prefers the newest run when several carry the artifact" {
  export RUN_IDS="333 222 111"
  export ART_PRESENT="333 111"
  run env REPO=o/r WORKFLOW=ci.yml SHA="${SHA}" ARTIFACT_NAME=Artemis.war GITHUB_OUTPUT="${OUT}" "${SCRIPT}"
  [ "${status}" -eq 0 ]
  grep -q '^run_id=333$' "${OUT}"
}

@test "fails when no run for the SHA carries the artifact" {
  export RUN_IDS="222 111"
  export ART_PRESENT=""
  run env REPO=o/r WORKFLOW=ci.yml SHA="${SHA}" ARTIFACT_NAME=Artemis.war GITHUB_OUTPUT="${OUT}" "${SCRIPT}"
  [ "${status}" -eq 1 ]
  [[ "${output}" == *"No CI run"* ]]
}

@test "rejects a value that is not a full commit SHA (injection + abbreviation guard)" {
  run env REPO=o/r WORKFLOW=ci.yml SHA='bad; rm -rf /' ARTIFACT_NAME=Artemis.war "${SCRIPT}"
  [ "${status}" -eq 1 ]
  [[ "${output}" == *"40-char commit SHA"* ]]

  run env REPO=o/r WORKFLOW=ci.yml SHA=da39a3e ARTIFACT_NAME=Artemis.war "${SCRIPT}"
  [ "${status}" -eq 1 ]
  [[ "${output}" == *"40-char commit SHA"* ]]
}
