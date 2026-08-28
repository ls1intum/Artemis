#!/bin/bash
# Runs a Gradle test command and, if it hangs, thread-dumps every JVM before CI kills the run.
# A deadlocked test produces no output and is otherwise killed by the Gradle task timeout
# (gradle/test.gradle) with no clue which thread hung. A dump to a separate artifact is used over
# `kill -3`, whose dump would be buried in the multi-100k-line tests.log.
# Usage: run_with_hang_dump.sh <command> [args...]
set -euo pipefail

# The window this watchdog works in, all of it inside the 55-minute Gradle Test task timeout:
#   - nothing happens before EARLIEST_DUMP_MINUTES, so a normal run is never touched
#   - between then and LATEST_DUMP_MINUTES a dump happens as soon as the run goes quiet
#   - after LATEST_DUMP_MINUTES no dump is started, leaving DUMP_PHASE_SECONDS for the dump itself
readonly EARLIEST_DUMP_MINUTES=45
readonly LATEST_DUMP_MINUTES=50
# The suite is only considered hung if it also stopped producing output. The full run legitimately takes
# 40-49 min on a loaded runner while still making progress, and dumping a live JVM costs it a safepoint
# pause, so an elapsed-time gate on its own fired on runs that were merely slow.
readonly SILENCE_MINUTES=5
readonly POLL_SECONDS=30
# Budget for the whole dump phase across every JVM, not per JVM: with a per-JVM timeout one unreachable
# process could eat the entire remaining window and the JVMs that mattered would never be dumped.
readonly DUMP_PHASE_SECONDS=240
readonly DUMP_DIR=build/hang-thread-dumps
readonly LOG_FILE=tests.log

log_size() {
    [ -f "$LOG_FILE" ] && wc -c < "$LOG_FILE" || echo 0
}

dump_all_jvms() {
    local deadline=$(( $(date +%s) + DUMP_PHASE_SECONDS ))
    mkdir -p "$DUMP_DIR"
    # A dump only happens once the run is already hung and therefore already lost to the Gradle timeout, so
    # a dump cut short here costs nothing. What must not happen is dumping a *healthy* JVM and wedging it,
    # which is what the silence gate above prevents.
    jcmd -l | awk '{print $1}' | while read -r pid; do
        local remaining=$(( deadline - $(date +%s) ))
        if [ "$remaining" -le 0 ]; then
            echo "::warning::Dump budget of ${DUMP_PHASE_SECONDS}s exhausted; skipping the remaining JVMs."
            break
        fi
        # `jcmd Thread.print -l` rather than `jstack -l`: same deadlock and lock information, and it is the
        # supported way to ask a live JVM for a dump.
        timeout "$remaining" jcmd "$pid" Thread.print -l > "$DUMP_DIR/threaddump-${pid}.txt" 2>&1 || true
    done
}

watch_for_hang() {
    local elapsed=0 silent_for=0 last_size
    local earliest=$(( EARLIEST_DUMP_MINUTES * 60 ))
    local latest=$(( LATEST_DUMP_MINUTES * 60 ))
    local silence=$(( SILENCE_MINUTES * 60 ))
    last_size=$(log_size)

    while [ "$elapsed" -lt "$latest" ]; do
        # `|| return`: when the watchdog is reaped on a normal finish the sleep is interrupted, so stop
        # watching — only a wait that runs its course means the command is still going.
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

        # Keep watching until BOTH conditions hold, rather than testing silence once at a fixed moment: a
        # run that goes quiet at minute 44 has only a minute of silence at minute 45, and checking there and
        # then would let exactly the hang this exists to catch go undumped.
        if [ "$elapsed" -ge "$earliest" ] && [ "$silent_for" -ge "$silence" ]; then
            echo "::warning::No output for ${SILENCE_MINUTES} min after ${elapsed}s — dumping JVM threads (likely hang); see the Server Test Thread Dumps artifact."
            dump_all_jvms
            return
        fi
    done

    echo "::notice::Still running after ${LATEST_DUMP_MINUTES} min but producing output; not dumping (a dump this late would not finish before the Gradle timeout)."
}

watch_for_hang &
watchdog_pid=$!
# Reap the watchdog *and* its `sleep` child (`pkill -P`); killing the subshell alone leaves the sleep
# orphaned for the runner to clean up on every run.
trap 'pkill -P "$watchdog_pid" 2>/dev/null || true; kill "$watchdog_pid" 2>/dev/null || true' EXIT INT TERM

"$@" 2>&1 | tee "$LOG_FILE"
