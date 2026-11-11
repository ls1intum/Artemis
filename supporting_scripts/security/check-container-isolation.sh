#!/usr/bin/env bash
#
# Checks that a build container on the given Docker network cannot reach what it must not reach: a service on the host and a container next to it.
# It starts the same kind of container that Artemis starts for a build, on the network that you pass, and expects every connection to fail.
#
# Usage: supporting_scripts/security/check-container-isolation.sh [NETWORK]
#   NETWORK  the Docker network of the build containers, default "none", the strictest one. For a network of your own, a failing check names what
#            a build can reach there, and the firewall rules of your installation have to close it (see the hardening checklist of the administrator guide).
#
# The script needs Docker and pulls the busybox image. It starts a listener on a free port of the host and removes everything it created.
set -Eeuo pipefail

NETWORK="${1:-none}"
# Pinned by digest. The tag is in the comment.
BUSYBOX_IMAGE="busybox@sha256:a7dbc319b1438115a207e11930dc4bb3e643dcb3ec0bdd694bb36cd8fad289ec" # 1.37.0
LISTENER_PORT="${LISTENER_PORT:-48123}"
SIBLING_NAME="artemis-isolation-sibling-$$"
LISTENER_PID=""
FAILURES=0

cleanup() {
    if [ -n "${LISTENER_PID}" ]; then
        kill "${LISTENER_PID}" 2>/dev/null || true
    fi
    docker rm --force "${SIBLING_NAME}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

if [ "${NETWORK}" = "host" ] || [[ "${NETWORK}" == container:* ]]; then
    echo "The network ${NETWORK} shares a network namespace and is refused by Artemis at startup. Nothing to check." >&2
    exit 2
fi

echo "Starting a listener on port ${LISTENER_PORT} of the host"
python3 -m http.server "${LISTENER_PORT}" --bind 0.0.0.0 >/dev/null 2>&1 &
LISTENER_PID=$!
sleep 1
if ! kill -0 "${LISTENER_PID}" 2>/dev/null || ! python3 -c "import socket;socket.create_connection(('127.0.0.1',${LISTENER_PORT}),2)"; then
    echo "The listener on port ${LISTENER_PORT} did not start." >&2
    exit 2
fi

# A sibling container on the default network, which a build container must not reach either.
docker run --detach --name "${SIBLING_NAME}" "${BUSYBOX_IMAGE}" sh -c 'nc -l -p 8080 -k -e true >/dev/null' >/dev/null
SIBLING_IP="$(docker inspect --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "${SIBLING_NAME}")"
if [ -z "${SIBLING_IP}" ]; then
    echo "The sibling container did not get an address." >&2
    exit 2
fi
# Wait until the sibling listens, so that an unreachable result means isolation and not a late start.
for _ in 1 2 3 4 5 6 7 8 9 10; do
    docker run --rm "${BUSYBOX_IMAGE}" nc -w 1 -z "${SIBLING_IP}" 8080 >/dev/null 2>&1 && break
    sleep 1
done

# Runs a command in a build-like container and reports whether it succeeded.
in_container() {
    docker run --rm --network "${NETWORK}" --add-host=host.docker.internal:host-gateway "${BUSYBOX_IMAGE}" sh -c "$1" >/dev/null 2>&1
}

expect_unreachable() {
    local description="$1" command="$2"
    if in_container "${command}"; then
        echo "FAIL  a build container on '${NETWORK}' can reach ${description}"
        FAILURES=$((FAILURES + 1))
    else
        echo "ok    ${description} is not reachable"
    fi
}

expect_unreachable "a service on the host (host.docker.internal:${LISTENER_PORT})" "wget -q -T 3 -O /dev/null http://host.docker.internal:${LISTENER_PORT}/"
expect_unreachable "a sibling container (${SIBLING_IP}:8080)" "nc -w 3 -z ${SIBLING_IP} 8080"
expect_unreachable "the internet (1.1.1.1:443)" "nc -w 3 -z 1.1.1.1 443"
expect_unreachable "a name server (example.com)" "nslookup -timeout=3 example.com"

if [ "${FAILURES}" -ne 0 ]; then
    echo "${FAILURES} check(s) failed for the network '${NETWORK}'." >&2
    exit 1
fi
echo "All checks passed for the network '${NETWORK}'."
