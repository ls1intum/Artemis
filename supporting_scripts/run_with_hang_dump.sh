#!/bin/bash
# Runs a Gradle test command and, if it hangs, thread-dumps every JVM before CI kills the run.
# A deadlocked test produces no output and is otherwise killed by the Gradle task timeout
# (gradle/test.gradle) with no clue which thread hung. A dump to a separate artifact is used over
# `kill -3`, whose dump would be buried in the multi-100k-line tests.log.
# Usage: run_with_hang_dump.sh <command> [args...]
set -euo pipefail

# The window this watchdog works in, all of it inside the 70-minute Gradle Test task timeout:
#   - nothing happens before EARLIEST_DUMP_MINUTES, so a normal run is never touched
#   - between then and LATEST_DUMP_MINUTES a dump starts as soon as the run goes quiet
#   - after LATEST_DUMP_MINUTES no dump is started, leaving DUMP_PHASE_SECONDS for the dump itself
readonly EARLIEST_DUMP_MINUTES=45
readonly LATEST_DUMP_MINUTES=65
# The suite is only considered hung if it also stopped producing output. The full run legitimately takes
# 40-49 min on a loaded runner while still making progress, and dumping a live JVM costs it a safepoint
# pause, so an elapsed-time gate on its own fired on runs that were merely slow.
readonly SILENCE_MINUTES=5
readonly POLL_SECONDS=30
# Budget for the whole dump phase across every JVM, not per JVM: with a per-JVM timeout one unreachable
# process could eat the entire remaining window and the JVMs that mattered would never be dumped.
readonly DUMP_PHASE_SECONDS=240
# `timeout` only sends TERM, which a jcmd wedged in an attach can ignore, so every bounded call gets a
# --kill-after grace as well. The grace is reserved out of the shared budget rather than added to it, so
# the last KILL still lands inside DUMP_PHASE_SECONDS.
readonly DUMP_GRACE_SECONDS=15
readonly JVM_LIST_SECONDS=30
readonly DUMP_DIR=build/hang-thread-dumps
readonly LOG_FILE=tests.log

now() { date +%s; }

log_size() {
    [ -f "$LOG_FILE" ] && wc -c < "$LOG_FILE" || echo 0
}

dump_all_jvms() {
    local deadline=$(( $(now) + DUMP_PHASE_SECONDS ))
    mkdir -p "$DUMP_DIR"

    # Bounded as well: `jcmd -l` attaches to every JVM to read its name and can hang on the very process
    # this is here to diagnose. Captured into a variable rather than piped, so the loop below runs in this
    # shell and its deadline check can actually stop it.
    local pids=''
    # Only numeric first fields: jcmd can emit a warning or an attach diagnostic, and passing one of those on as a
    # pid would create a junk dump file and waste part of the shared budget.
    pids=$(timeout --kill-after="$DUMP_GRACE_SECONDS" "$JVM_LIST_SECONDS" jcmd -l 2>/dev/null | awk '$1 ~ /^[0-9]+$/ {print $1}') || true
    if [ -z "$pids" ]; then
        echo "::warning::Could not list JVMs within ${JVM_LIST_SECONDS}s; no thread dumps captured."
        return
    fi

    local pid remaining budget
    for pid in $pids; do
        remaining=$(( deadline - $(now) ))
        # Needs room for TERM plus the KILL grace, or the dump cannot finish inside the shared budget.
        if [ "$remaining" -le $(( DUMP_GRACE_SECONDS + 1 )) ]; then
            echo "::warning::Dump budget of ${DUMP_PHASE_SECONDS}s exhausted; skipping the remaining JVMs."
            break
        fi
        budget=$(( remaining - DUMP_GRACE_SECONDS ))
        # `jcmd Thread.print -l` rather than `jstack -l`: same deadlock and lock information, and it is the
        # supported way to ask a live JVM for a dump.
        timeout --kill-after="$DUMP_GRACE_SECONDS" "$budget" jcmd "$pid" Thread.print -l > "$DUMP_DIR/threaddump-${pid}.txt" 2>&1 || true
    done
}

watch_for_hang() {
    local started_at earliest latest silence last_size last_change_at elapsed silent_for
    started_at=$(now)
    earliest=$(( EARLIEST_DUMP_MINUTES * 60 ))
    latest=$(( LATEST_DUMP_MINUTES * 60 ))
    silence=$(( SILENCE_MINUTES * 60 ))
    last_size=$(log_size)
    last_change_at=$started_at

    while :; do
        # `|| return`: when the watchdog is reaped on a normal finish the sleep is interrupted, so stop
        # watching — only a wait that runs its course means the command is still going.
        sleep "$POLL_SECONDS" || return

        # Both windows are measured against the clock, not by counting polls. A loaded runner can resume a
        # sleep late, and accumulating POLL_SECONDS would then under-count real time - enough drift and a
        # dump could start after the real deadline and still be running when Gradle kills the task at 70 min.
        elapsed=$(( $(now) - started_at ))

        local size
        size=$(log_size)
        if [ "$size" != "$last_size" ]; then
            last_size=$size
            last_change_at=$(now)
        fi
        silent_for=$(( $(now) - last_change_at ))

        # Both conditions are checked every poll rather than silence being tested once at a fixed moment: a
        # run that goes quiet at minute 44 has only a minute of silence at minute 45, and checking there and
        # then would let exactly the hang this exists to catch go undumped.
        if [ "$elapsed" -ge "$earliest" ] && [ "$elapsed" -le "$latest" ] && [ "$silent_for" -ge "$silence" ]; then
            echo "::warning::No output for ${silent_for}s after ${elapsed}s — dumping JVM threads (likely hang); see the Server Test Thread Dumps artifact."
            dump_all_jvms
            return
        fi

        # Past the deadline a dump can no longer finish before Gradle kills the task, so report and stop watching. The
        # deadline is also part of the condition above rather than only here: a poll can arrive late on a loaded runner,
        # and without it a run that had gone quiet would start a full dump phase after the deadline had already passed.
        if [ "$elapsed" -gt "$latest" ]; then
            echo "::notice::Still running after ${elapsed}s; last output ${silent_for}s ago. Not dumping: one started this late could not finish before the Gradle timeout."
            return
        fi
    done
}

watch_for_hang &
watchdog_pid=$!
# Reap the watchdog *and* its `sleep` child (`pkill -P`); killing the subshell alone leaves the sleep
# orphaned for the runner to clean up on every run.
trap 'pkill -P "$watchdog_pid" 2>/dev/null || true; kill "$watchdog_pid" 2>/dev/null || true' EXIT INT TERM

"$@" 2>&1 | tee "$LOG_FILE"
