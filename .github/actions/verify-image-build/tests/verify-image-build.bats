#!/usr/bin/env bats
# Unit tests for verify-image-build.sh with a mocked `docker` on PATH.

setup() {
  SCRIPT="${BATS_TEST_DIRNAME}/../verify-image-build.sh"
  BIN="$(mktemp -d)"
  cat > "${BIN}/docker" <<'EOF'
#!/usr/bin/env bash
# Fake docker: `login` always succeeds; `manifest inspect <ref>` succeeds only for EXPECTED_REF.
if [ "$1" = "login" ]; then exit 0; fi
if [ "$1" = "manifest" ] && [ "$2" = "inspect" ]; then
  [ "$3" = "${EXPECTED_REF}" ] && exit 0 || exit 1
fi
exit 0
EOF
  chmod +x "${BIN}/docker"
  PATH="${BIN}:${PATH}"
}

teardown() { rm -rf "${BIN}"; }

SHA_A=da39a3ee5e6b4b0d3255bfef95601890afd80709
SHA_B=1111111111111111111111111111111111111111

@test "passes when the image for the SHA exists" {
  export EXPECTED_REF="ghcr.io/ls1intum/artemis:sha-${SHA_A}"
  run env IMAGE=ghcr.io/ls1intum/artemis SHA="${SHA_A}" "${SCRIPT}"
  [ "${status}" -eq 0 ]
  [[ "${output}" == *"Deployable image present"* ]]
}

@test "fails when no image exists for the SHA (the Helios#1196 case: verify by image, not run)" {
  export EXPECTED_REF="ghcr.io/ls1intum/artemis:sha-${SHA_B}"
  run env IMAGE=ghcr.io/ls1intum/artemis SHA="${SHA_A}" "${SCRIPT}"
  [ "${status}" -eq 1 ]
  [[ "${output}" == *"No deployable image"* ]]
}

@test "rejects a SHA that is not a full 40-char commit" {
  # The tag is the full sha-<commit>, so an abbreviated value looks up a tag that was never pushed.
  run env IMAGE=ghcr.io/ls1intum/artemis SHA=da39a3e "${SCRIPT}"
  [ "${status}" -eq 1 ]
  [[ "${output}" == *"40-char commit SHA"* ]]

  run env IMAGE=ghcr.io/ls1intum/artemis SHA='not a sha' "${SCRIPT}"
  [ "${status}" -eq 1 ]
  [[ "${output}" == *"40-char commit SHA"* ]]
}
