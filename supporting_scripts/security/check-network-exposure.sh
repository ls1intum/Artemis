#!/usr/bin/env bash
#
# Checks from a machine outside your installation that only the intended ports of a host are reachable.
# The services that hold the state of Artemis (database, queue store, cluster port, message broker) and the application port behind the reverse proxy must not
# be reachable from the internet or from the networks of the build containers. Only the reverse proxy (HTTP and HTTPS) and, where Git over SSH is offered, the SSH
# port of the version control system should answer.
#
# Run it from a machine that is not part of the installation, once per host that has a public or student-facing address, and from a build agent or a container
# on the build network for the container view (see check-container-isolation.sh for the latter). It only opens TCP connections and sends no data.
#
# Usage: supporting_scripts/security/check-network-exposure.sh HOST [HOST...]
#
# Environment:
#   INTERNAL_PORTS   space separated ports that must be closed (default: the standard ports of the services below)
#   EXPECTED_PORTS   space separated ports that must be open (default: 443)
#   OPTIONAL_PORTS   space separated ports that may be open, reported as information (default: 80 7921)
#   CONNECT_TIMEOUT  seconds to wait for a connection (default 3)
#
# Exit code: 0 if no internal port is open and every expected port is open, 1 otherwise, 2 for a usage error.
set -Eeuo pipefail

if [ "$#" -lt 1 ]; then
    echo "Usage: $0 HOST [HOST...]" >&2
    exit 2
fi
if ! command -v nc >/dev/null 2>&1; then
    echo "nc (netcat) is required." >&2
    exit 2
fi

# 5701-5703 Hazelcast, 6379 Redis or Valkey, 5432 PostgreSQL, 3306 MySQL, 61613 and 61616 ActiveMQ, 8080 Artemis behind the reverse proxy, 8761 service registry
INTERNAL_PORTS="${INTERNAL_PORTS:-5701 5702 5703 6379 5432 3306 61613 61616 8080 8761}"
EXPECTED_PORTS="${EXPECTED_PORTS:-443}"
OPTIONAL_PORTS="${OPTIONAL_PORTS:-80 7921}"
CONNECT_TIMEOUT="${CONNECT_TIMEOUT:-3}"

FAILURES=0

is_open() {
    nc -z -w "${CONNECT_TIMEOUT}" "$1" "$2" >/dev/null 2>&1
}

for host in "$@"; do
    echo "Host ${host}"
    for port in ${INTERNAL_PORTS}; do
        if is_open "${host}" "${port}"; then
            echo "FAIL  port ${port} is reachable but belongs to an internal service"
            FAILURES=$((FAILURES + 1))
        else
            echo "ok    port ${port} is closed"
        fi
    done
    for port in ${EXPECTED_PORTS}; do
        if is_open "${host}" "${port}"; then
            echo "ok    port ${port} is open, as expected"
        else
            echo "FAIL  port ${port} is expected to be open but is not reachable"
            FAILURES=$((FAILURES + 1))
        fi
    done
    for port in ${OPTIONAL_PORTS}; do
        if is_open "${host}" "${port}"; then
            echo "info  port ${port} is open"
        else
            echo "info  port ${port} is closed"
        fi
    done
done

if [ "${FAILURES}" -ne 0 ]; then
    echo "${FAILURES} check(s) failed. Close the listed ports with the firewall of the host or bind the service to a private interface." >&2
    exit 1
fi
echo "All checks passed."
