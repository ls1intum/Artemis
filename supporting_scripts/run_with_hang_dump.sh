#!/bin/bash
# Runs a Gradle test command and, if it hangs, thread-dumps every JVM before CI kills the run.
# A deadlocked test produces no output and is otherwise killed by the Gradle task timeout
# (gradle/test.gradle) with no clue which thread hung. jstack -l (deadlock + lock info) to a separate
# artifact is used over `kill -3`, whose dump would be buried in the multi-100k-line tests.log.
# Usage: run_with_hang_dump.sh <command> [args...]
set -euo pipefail

# Must stay below the Gradle Test task timeout (55 min) so the JVMs are still alive when dumped.
readonly WATCHDOG_MINUTES=45
readonly DUMP_DIR=build/hang-thread-dumps

dump_hung_jvms() {
    # `|| return`: when the watchdog is reaped on a normal finish the sleep is interrupted, so skip
    # the dump — only a sleep that runs its full course means the command actually hung.
    sleep "$(( WATCHDOG_MINUTES * 60 ))" || return
    echo "::warning::No completion after ${WATCHDOG_MINUTES} min — dumping JVM threads (likely hang); see the Server Test Thread Dumps artifact."
    mkdir -p "$DUMP_DIR"
    jcmd -l | awk '{print $1}' | while read -r pid; do
        # `timeout`: attaching to a truly wedged JVM can itself hang; don't let one block the rest.
        timeout 60 jstack -l "$pid" > "$DUMP_DIR/threaddump-${pid}.txt" 2>&1 || true
    done
}

dump_hung_jvms &
watchdog_pid=$!
# Reap the watchdog *and* its `sleep` child (`pkill -P`); killing the subshell alone leaves the sleep
# orphaned for the runner to clean up on every run.
trap 'pkill -P "$watchdog_pid" 2>/dev/null || true; kill "$watchdog_pid" 2>/dev/null || true' EXIT INT TERM

"$@" 2>&1 | tee tests.log
