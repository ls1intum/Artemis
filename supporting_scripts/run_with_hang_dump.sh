#!/bin/bash
# Runs a Gradle test command and, if it hangs, thread-dumps every JVM before CI kills the run.
# A deadlocked test produces no output and is otherwise killed by the Gradle task timeout
# (gradle/test.gradle) with no clue which thread hung. A dump to a separate artifact is used over
# `kill -3`, whose dump would be buried in the multi-100k-line tests.log.
# Usage: run_with_hang_dump.sh <command> [args...]
set -euo pipefail

# Must stay below the Gradle Test task timeout (55 min) so the JVMs are still alive when dumped.
readonly WATCHDOG_MINUTES=45
# The suite is only considered hung if it also stopped producing output. The full run legitimately takes
# 40-49 min on a loaded runner while still making progress, and a dump costs a healthy JVM a safepoint
# pause, so the elapsed-time gate alone fired on runs that were merely slow.
readonly SILENCE_MINUTES=5
readonly POLL_SECONDS=30
# Deliberately generous, and deliberately not a way to bound a wedged attach. Killing the dumper part way
# through leaves the target JVM at a safepoint it never leaves: three CI runs were lost to exactly that,
# each with a header-only dump file, the last test result logged about a second after the dump began, and
# tests in unrelated classes stopping at the same instant. The one run whose dump completed survived.
readonly DUMP_TIMEOUT_SECONDS=600
readonly DUMP_DIR=build/hang-thread-dumps
readonly LOG_FILE=tests.log

log_size() {
    [ -f "$LOG_FILE" ] && wc -c < "$LOG_FILE" || echo 0
}

dump_hung_jvms() {
    local elapsed=0 silent_for=0 last_size
    last_size=$(log_size)

    while [ "$elapsed" -lt "$(( WATCHDOG_MINUTES * 60 ))" ]; do
        # `|| return`: when the watchdog is reaped on a normal finish the sleep is interrupted, so skip
        # the dump — only a wait that runs its full course means the command actually hung.
        sleep "$POLL_SECONDS" || return
        elapsed=$(( elapsed + POLL_SECONDS ))

        local size
        size=$(log_size)
        if [ "$size" = "$last_size" ]; then
            silent_for=$(( silent_for + POLL_SECONDS ))
        else
            silent_for=0
            last_size=$size
        fi
    done

    if [ "$silent_for" -lt "$(( SILENCE_MINUTES * 60 ))" ]; then
        echo "::notice::Still running after ${WATCHDOG_MINUTES} min but producing output; not dumping."
        return
    fi

    echo "::warning::No output for ${SILENCE_MINUTES} min after ${WATCHDOG_MINUTES} min — dumping JVM threads (likely hang); see the Server Test Thread Dumps artifact."
    mkdir -p "$DUMP_DIR"
    jcmd -l | awk '{print $1}' | while read -r pid; do
        # `jcmd Thread.print -l` rather than `jstack -l`: same deadlock and lock information, and it is the
        # supported way to ask a live JVM for a dump. The timeout is a backstop against a JVM that is already
        # unreachable, not a budget — see DUMP_TIMEOUT_SECONDS.
        timeout "$DUMP_TIMEOUT_SECONDS" jcmd "$pid" Thread.print -l > "$DUMP_DIR/threaddump-${pid}.txt" 2>&1 || true
    done
}

dump_hung_jvms &
watchdog_pid=$!
# Reap the watchdog *and* its `sleep` child (`pkill -P`); killing the subshell alone leaves the sleep
# orphaned for the runner to clean up on every run.
trap 'pkill -P "$watchdog_pid" 2>/dev/null || true; kill "$watchdog_pid" 2>/dev/null || true' EXIT INT TERM

"$@" 2>&1 | tee "$LOG_FILE"
