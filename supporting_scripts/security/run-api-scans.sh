#!/usr/bin/env bash
#
# Scans a running Artemis with two tools that look at it from the outside:
#   - Schemathesis sends generated requests to the reading endpoints of the OpenAPI description (config/openapi/openapi.yaml) as a signed-in student
#     and reports every response that is a server error or does not match the description.
#   - The OWASP ZAP baseline scan crawls the client and reports header, cookie and configuration findings, without attacking anything.
#
# Only reading requests are sent. Use a throwaway installation, such as the one of the E2E runners, and not a production system.
#
# Usage: supporting_scripts/security/run-api-scans.sh [BASE_URL] [OUTPUT_DIRECTORY]
#   BASE_URL           the address of the server, default https://localhost (the multi-node E2E stack); http://localhost:8080 for the single-node runner
#   OUTPUT_DIRECTORY   where the reports are written, default security-scan-reports
#
# Environment: SCAN_USERNAME and SCAN_PASSWORD (default: the student of the E2E database), SCHEMATHESIS_MAX_EXAMPLES (default 3),
#              FAIL_ON_FINDINGS (default 0: the findings are reported and the script succeeds; 1 fails the script when Schemathesis reports a finding).
set -Eeuo pipefail

BASE_URL="${1:-https://localhost}"
OUTPUT_DIRECTORY="${2:-security-scan-reports}"
SCAN_USERNAME="${SCAN_USERNAME:-artemis_test_user_1}"
SCAN_PASSWORD="${SCAN_PASSWORD:-artemis_test_user_1}"
SCHEMATHESIS_MAX_EXAMPLES="${SCHEMATHESIS_MAX_EXAMPLES:-3}"
FAIL_ON_FINDINGS="${FAIL_ON_FINDINGS:-0}"

# Pinned by digest, like the actions of the workflows. The tags are in the comments.
SCHEMATHESIS_IMAGE="schemathesis/schemathesis@sha256:ddf8b3c9e0b3ee7edfa65dad4d0c0e05c53fb5d92f99db379d40da01883474c7" # 4.33.1
ZAP_IMAGE="ghcr.io/zaproxy/zaproxy@sha256:9624669db72f54d86c4b46a7064518bd9bf79d8c61a4c31befa9923489f328af"            # stable

REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# The scanner containers run as another user than the one who runs this script and write their reports into the directory. A directory that this script creates is opened
# for them. An existing directory, such as the current one, keeps its permissions and has to be writable for them.
OUTPUT_DIRECTORY_CREATED=0
if [ ! -d "${OUTPUT_DIRECTORY}" ]; then
    mkdir -p "${OUTPUT_DIRECTORY}"
    OUTPUT_DIRECTORY_CREATED=1
fi
OUTPUT_DIRECTORY="$(cd "${OUTPUT_DIRECTORY}" && pwd)"
if [ "${OUTPUT_DIRECTORY_CREATED}" -eq 1 ]; then
    # Writable for the scanner users. The sticky bit keeps one of them from deleting or replacing the reports of another, and the owner of the directory can still remove them.
    chmod 1777 "${OUTPUT_DIRECTORY}"
fi

# A local server may listen on the loopback interface only, as the development server of the client does. On Linux the scanners then share the network of the host and use the
# address as it is. Docker Desktop does not offer that, there a container reaches the host through host.docker.internal.
LOCAL_TARGET=0
if printf '%s' "${BASE_URL}" | grep -Eq '^https?://(localhost|127\.0\.0\.1)([:/]|$)'; then
    LOCAL_TARGET=1
fi
if [ "${LOCAL_TARGET}" -eq 1 ] && [ "$(uname -s)" = "Linux" ]; then
    CONTAINER_URL="${BASE_URL}"
    DOCKER_HOST_ARGS=(--network host)
else
    CONTAINER_URL="$(printf '%s' "${BASE_URL}" | sed -E 's#^(https?://)(localhost|127\.0\.0\.1)([:/]|$)#\1host.docker.internal\3#')"
    DOCKER_HOST_ARGS=(--add-host=host.docker.internal:host-gateway)
fi
# The reports of an earlier run must not make this run look complete.
rm -f "${OUTPUT_DIRECTORY}"/junit-*.xml "${OUTPUT_DIRECTORY}"/zap-baseline.json "${OUTPUT_DIRECTORY}"/zap-baseline.html

# The E2E stack uses a self-signed certificate, so verification is only skipped for a local address. Set CURL_CA_BUNDLE for other installations.
CURL_TLS_ARGS=()
if printf '%s' "${BASE_URL}" | grep -Eq '^https?://(localhost|127\.0\.0\.1)([:/]|$)'; then
    CURL_TLS_ARGS=(--insecure)
fi

echo "Signing in as ${SCAN_USERNAME} at ${BASE_URL}"
JWT="$(curl --silent --show-error ${CURL_TLS_ARGS[@]+"${CURL_TLS_ARGS[@]}"} --fail --dump-header - --output /dev/null \
    --header 'Content-Type: application/json' \
    --data "{\"username\":\"${SCAN_USERNAME}\",\"password\":\"${SCAN_PASSWORD}\",\"rememberMe\":false}" \
    "${BASE_URL}/api/core/public/authenticate" | tr -d '\r' | sed -n 's/^[Ss]et-[Cc]ookie: jwt=\([^;]*\);.*/\1/p' | head -n 1)"
if [ -z "${JWT}" ]; then
    echo "The sign-in did not set the jwt cookie." >&2
    exit 2
fi

SCHEMATHESIS_STATUS=0
echo "Schemathesis: reading endpoints, ${SCHEMATHESIS_MAX_EXAMPLES} examples each"
docker run --rm "${DOCKER_HOST_ARGS[@]}" \
    --volume "${REPOSITORY_ROOT}/config/openapi:/spec:ro" --volume "${OUTPUT_DIRECTORY}:/out" \
    "${SCHEMATHESIS_IMAGE}" run /spec/openapi.yaml \
    --url "${CONTAINER_URL}" --tls-verify false \
    --header "Cookie: jwt=${JWT}" \
    --include-method GET \
    --exclude-path-regex '(/admin/|/management/|/internal/|/export|/download|/files/)' \
    --checks not_a_server_error,response_schema_conformance \
    --max-examples "${SCHEMATHESIS_MAX_EXAMPLES}" \
    --report junit --report-dir /out || SCHEMATHESIS_STATUS=$?

ZAP_STATUS=0
echo "ZAP baseline scan"
docker run --rm "${DOCKER_HOST_ARGS[@]}" --volume "${OUTPUT_DIRECTORY}:/zap/wrk:rw" "${ZAP_IMAGE}" \
    zap-baseline.py -t "${CONTAINER_URL}" -I -m 2 -r zap-baseline.html -J zap-baseline.json || ZAP_STATUS=$?

echo "Reports are in ${OUTPUT_DIRECTORY}"
echo "Schemathesis exit code ${SCHEMATHESIS_STATUS}, ZAP exit code ${ZAP_STATUS}"

# A scan that could not run is an error, whatever the setting for findings: Docker reports 125 to 127 when it could not start the container, ZAP exits with 3 on an
# operational error, and a scan that completed has written its report.
EXECUTION_ERROR=0
if [ "${SCHEMATHESIS_STATUS}" -ge 125 ] || ! ls "${OUTPUT_DIRECTORY}"/junit-*.xml >/dev/null 2>&1; then
    echo "Schemathesis did not complete: there is no report." >&2
    EXECUTION_ERROR=1
fi
# Schemathesis writes a request that did not get an answer as an error of the report, and a check that failed as a failure. Errors mean that the scan did not complete.
if grep -qs '<error' "${OUTPUT_DIRECTORY}"/junit-*.xml; then
    echo "Schemathesis reported errors of its own, such as a request that got no answer." >&2
    EXECUTION_ERROR=1
fi
if [ "${ZAP_STATUS}" -ge 3 ] || [ ! -s "${OUTPUT_DIRECTORY}/zap-baseline.json" ]; then
    echo "The ZAP baseline scan did not complete: there is no report or it reported an error." >&2
    EXECUTION_ERROR=1
fi
if [ "${EXECUTION_ERROR}" -ne 0 ]; then
    exit 2
fi
# ZAP exits with 1 for failures and 2 for warnings. Warnings are the normal result of a first scan and are triaged from the report.
if [ "${ZAP_STATUS}" -eq 1 ]; then
    exit 1
fi
# Schemathesis reports every finding as a failure. Until the existing ones are fixed, a run reports them and does not fail.
if [ "${SCHEMATHESIS_STATUS}" -ne 0 ] && [ "${FAIL_ON_FINDINGS}" = "1" ]; then
    exit 1
fi
