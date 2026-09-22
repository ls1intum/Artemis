import { URL } from 'node:url';
// The synchronous face of the Tailwind oracle. Failures are kept apart: a
// theme that cannot be built is unavailable on its own and retried, so
// other projects in the same editor session keep their answers, while a
// worker that dies or stalls is restarted once and only a second
// transport failure turns the oracle off for the process.
import { MessageChannel, receiveMessageOnPort, Worker } from 'node:worker_threads';
import { warnOnce } from '../project/warn.mjs';
// null: not started; false: off for the process.
let bridge = null;
let restarts = 0;
const FIRST_TIMEOUT = 15_000;
const TIMEOUT = 5_000;
const RETRY_AFTER = 5_000;
const REFRESH_AFTER = 1_000;
function stop() {
    if (bridge) {
        bridge.port.close();
        bridge.worker.terminate().catch(() => {});
    }
    bridge = null;
    // A new worker numbers its generations from one again.
    memos.clear();
}
// One restart is allowed; after that the oracle is off.
function transportFailed(reason) {
    stop();
    if (restarts++ >= 1) {
        bridge = false;
        warnOnce('tailwind:off', `The Tailwind worker failed twice (${reason}); class verification is unavailable for this run.`);
    }
    return null;
}
function start() {
    if (bridge) return bridge;
    if (bridge === false) return null;
    try {
        const channel = new MessageChannel();
        const worker = new Worker(new URL('./worker.mjs', import.meta.url), {
            workerData: { port: channel.port2 },
            transferList: [channel.port2],
        });
        // An error from a replaced worker is one failure arriving late.
        worker.on('error', (error) => {
            if (bridge && bridge.worker === worker) transportFailed(error.message);
        });
        // Replaced on the next question, not waited on for a full timeout.
        worker.on('exit', () => {
            if (bridge && bridge.worker === worker) stop();
        });
        worker.unref();
        channel.port1.unref();
        bridge = { worker, port: channel.port1, nextId: 1, cold: true };
        return bridge;
    } catch (error) {
        return transportFailed(error.message);
    }
}
function timeoutFor(live) {
    return live.cold ? FIRST_TIMEOUT : TIMEOUT;
}
function ask(cssFile, candidates) {
    const live = start();
    if (!live) return null;
    const id = live.nextId++;
    const shared = new SharedArrayBuffer(4);
    const flag = new Int32Array(shared);
    live.port.postMessage({ id, cssFile, candidates, shared });
    const waited = Atomics.wait(flag, 0, 0, timeoutFor(live));
    live.cold = false;
    if (waited === 'timed-out') return transportFailed('timed out');
    const received = receiveMessageOnPort(live.port);
    if (!received || received.message.id !== id) {
        return transportFailed('out-of-order answer');
    }
    // A worker that answers is healthy: a later failure starts over.
    restarts = 0;
    return received.message.answer;
}
const memos = new Map();
const failed = new Map();
function themeFailed(cssFile, reason) {
    failed.set(cssFile, { at: Date.now(), reason });
    memos.delete(cssFile);
    warnOnce(`tailwind:${cssFile}:${reason}`, `The Tailwind theme at ${cssFile} could not be built (${reason}); class verification is unavailable until the theme is fixed.`);
    return null;
}
// Null when the oracle is unavailable for this theme. Answers are
// remembered per theme and rechecked about once a second.
function readUnknownClasses(cssFile, candidates) {
    if (bridge === false) return null;
    const failure = failed.get(cssFile);
    if (failure) {
        if (Date.now() - failure.at < RETRY_AFTER) return null;
        failed.delete(cssFile);
    }
    const now = Date.now();
    let memo = memos.get(cssFile);
    const stale = !memo || now - memo.checkedAt >= REFRESH_AFTER;
    let unseen = candidates.filter((c) => !memo?.verdicts.has(c));
    if (unseen.length || stale) {
        let answer = ask(cssFile, [...new Set(unseen)]);
        if (!answer) return null;
        if (!answer.ok) return themeFailed(cssFile, answer.reason);
        if (!memo || memo.generation !== answer.generation) {
            // The theme was rebuilt, so nothing remembered still holds. One
            // that loads JavaScript needs a fresh worker: a module cache never
            // forgets an edited plugin.
            const rebuild = Boolean(memo && answer.hasModules);
            if (rebuild) stop();
            unseen = [...new Set(candidates)];
            // A rebuild always asks again: the fresh worker owes us a
            // generation number even when every candidate is already known.
            if (rebuild || unseen.length) {
                answer = ask(cssFile, unseen);
                if (!answer) return null;
                if (!answer.ok) return themeFailed(cssFile, answer.reason);
            }
            memo = {
                generation: answer.generation,
                checkedAt: now,
                hasModules: answer.hasModules,
                verdicts: new Map(),
            };
            memos.set(cssFile, memo);
        }
        memo.checkedAt = now;
        for (const token of unseen) memo.verdicts.set(token, true);
        for (const entry of answer.unknown) memo.verdicts.set(entry.token, entry);
    }
    const out = [];
    for (const token of candidates) {
        const verdict = memo.verdicts.get(token);
        if (verdict !== true && verdict !== undefined) out.push(verdict);
    }
    return out;
}
// A configured Angular theme must be verified by its compiler, never silently downgraded to grammar guesses.
export function unknownClasses(cssFile, candidates) {
    const result = readUnknownClasses(cssFile, candidates);
    if (result === null) throw new Error(`Cannot verify design-system classes in ${cssFile}: ${failed.get(cssFile)?.reason ?? 'Tailwind worker unavailable'}`);
    return result;
}

export function stopOracleForTests() {
    stop();
}
