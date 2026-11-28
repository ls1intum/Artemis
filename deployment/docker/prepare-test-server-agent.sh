#!/usr/bin/env bash
# Project the allowlisted settings from the Ansible-rendered core env file into
# a worker-only Compose env file. Do not source or log the input or its values.
set -Eeuo pipefail
source_file=${1:?Usage: prepare-test-server-agent.sh /path/to/artemis.env}
if [[ "${2:-}" == --detect-topology ]]; then
    # The external role renders this setting in the common env file for Jenkins,
    # LocalCI and other core-only installations. Do not print any setting values.
    topology=$(awk '
        /^SPRING_PROFILES_ACTIVE[[:space:]]*[:=]/ {
            if ($0 ~ /(^|[^a-z])(localci|buildagent)([^a-z]|$)/) print "localci"
            else if ($0 ~ /(^|[^a-z])core([^a-z]|$)/) print "core"
        }
    ' "$source_file")
    case "$topology" in
        localci) ;;
        core) echo core; exit 0 ;;
        *) echo "Missing or ambiguous Spring profiles in the rendered test-server configuration" >&2; exit 1 ;;
    esac
fi
umask 077
target="$source_file.runner.env"
temporary=$(mktemp "$target.XXXXXXXX")
trap 'rm -f -- "$temporary"' EXIT
awk '
    /^[A-Z][A-Z0-9_]*[[:space:]]*[:=]/ {
        key=$0
        sub(/[[:space:]]*[:=].*$/, "", key)
        if (key ~ /^ARTEMIS_CONTINUOUSINTEGRATION_(SPECIFYCONCURRENTBUILDS|CONCURRENTBUILDSIZE|IMAGEARCHITECTURE|PROXIES_(USESYSTEMPROXY|DEFAULT_(HTTPPROXY|HTTPSPROXY|NOPROXY))|IMAGECLEANUP_(ENABLED|EXPIRYDAYS|CLEANUPSCHEDULETIME)|CONTAINERCLEANUP_(EXPIRYMINUTES|CLEANUPSCHEDULEMINUTES)|CONTAINERFLAGSLIMIT_(MAXCPUCOUNT|MAXMEMORY|MAXMEMORYSWAP|ALLOWEDCUSTOMNETWORKS))$/ ||
            key ~ /^ARTEMIS_VERSIONCONTROL_(BUILDAGENTUSESSH|SSHTEMPLATECLONEURL|SSHHOSTKEYVERIFICATION|BUILDAGENTCLONETIMEOUTSECONDS)$/ ||
            key ~ /^ARTEMIS_(GIT_(NAME|EMAIL)|PROGRAMMING_MAVENCENTRALMIRRORURL)$/) print
    }
' "$source_file" > "$temporary"
mv -f -- "$temporary" "$target"
if [[ "${2:-}" == --detect-topology ]]; then echo localci; fi
