#!/usr/bin/env bats
# Unit tests for resolve-artifact-run.sh with a mocked `gh` on PATH.

setup() {
  SCRIPT="${BATS_TEST_DIRNAME}/../resolve-artifact-run.sh"
  BIN="$(mktemp -d)"
  cat > "${BIN}/gh" <<'EOF'
#!/usr/bin/env bash
# Fake `gh api`: emit a fixture response body for the endpoint, then run the real `--jq` filter over
# it. Emulating the already-filtered output instead would leave the script's jq expressions — notably
# the non-expired artifact filter — never executed, so a broken filter would still pass every test.
#   RUN_IDS      space-separated run ids, newest first (the order the runs API returns)
#   ART_FRESH    run ids whose artifact copy is NOT expired
#   ART_EXPIRED  run ids whose only artifact copy IS expired
endpoint=""; jq_expr=""
while [ "$#" -gt 0 ]; do
  case "$1" in
    api) shift ;;
    --jq) jq_expr="$2"; shift 2 ;;
    -f|-F|-H|-X) shift 2 ;;
    -*) shift ;;
    *) [ -z "${endpoint}" ] && endpoint="$1"; shift ;;
  esac
done

case "${endpoint}" in
  */actions/workflows/*/runs)
    runs=""
    for id in ${RUN_IDS:-}; do runs="${runs}${runs:+,}{\"id\":${id}}"; done
    body="{\"workflow_runs\":[${runs}]}" ;;
  */actions/runs/*/artifacts)
    id="${endpoint#*/actions/runs/}"; id="${id%/artifacts}"
    body='{"artifacts":[]}'
    for fresh in ${ART_FRESH:-}; do
      [ "${id}" = "${fresh}" ] && body='{"artifacts":[{"expired":false}]}'
    done
    for expired in ${ART_EXPIRED:-}; do
      [ "${id}" = "${expired}" ] && body='{"artifacts":[{"expired":true}]}'
    done ;;
  *) echo "fake gh: unexpected endpoint '${endpoint}'" >&2; exit 1 ;;
esac

printf '%s' "${body}" | jq -r "${jq_expr}"
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
  export ART_FRESH="111"
  run env REPO=o/r WORKFLOW=ci.yml SHA="${SHA}" ARTIFACT_NAME=Artemis.war GITHUB_OUTPUT="${OUT}" "${SCRIPT}"
  [ "${status}" -eq 0 ]
  grep -q '^run_id=111$' "${OUT}"
}

@test "prefers the newest run when several carry the artifact" {
  export RUN_IDS="333 222 111"
  export ART_FRESH="333 111"
  run env REPO=o/r WORKFLOW=ci.yml SHA="${SHA}" ARTIFACT_NAME=Artemis.war GITHUB_OUTPUT="${OUT}" "${SCRIPT}"
  [ "${status}" -eq 0 ]
  grep -q '^run_id=333$' "${OUT}"
}

@test "skips a run whose only copy of the artifact is expired" {
  # An expired artifact cannot be downloaded, so run 222 must not be resolved even though it is
  # newest and the API still lists the artifact.
  export RUN_IDS="222 111"
  export ART_EXPIRED="222"
  export ART_FRESH="111"
  run env REPO=o/r WORKFLOW=ci.yml SHA="${SHA}" ARTIFACT_NAME=Artemis.war GITHUB_OUTPUT="${OUT}" "${SCRIPT}"
  [ "${status}" -eq 0 ]
  grep -q '^run_id=111$' "${OUT}"
}

@test "fails when no run for the SHA carries the artifact" {
  export RUN_IDS="222 111"
  export ART_FRESH=""
  run env REPO=o/r WORKFLOW=ci.yml SHA="${SHA}" ARTIFACT_NAME=Artemis.war GITHUB_OUTPUT="${OUT}" "${SCRIPT}"
  [ "${status}" -eq 1 ]
  [[ "${output}" == *"No CI run"* ]]
}

@test "rejects a SHA that is not a full 40-char commit" {
  # The runs API filters on the full head_sha, so an abbreviated value silently matches nothing.
  run env REPO=o/r WORKFLOW=ci.yml SHA=da39a3e ARTIFACT_NAME=Artemis.war "${SCRIPT}"
  [ "${status}" -eq 1 ]
  [[ "${output}" == *"40-char commit SHA"* ]]

  run env REPO=o/r WORKFLOW=ci.yml SHA='not a sha' ARTIFACT_NAME=Artemis.war "${SCRIPT}"
  [ "${status}" -eq 1 ]
  [[ "${output}" == *"40-char commit SHA"* ]]
}
