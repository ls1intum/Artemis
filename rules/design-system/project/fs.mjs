// Filesystem helpers, memoized for the duration of a lint burst.
import * as fs from 'node:fs';
// How long any answer derived from the filesystem stays fresh. A run
// visits many files in a burst and asks the same questions for each; one
// second answers from memory without letting an editor go stale. The
// theme signature caches re-stat on the same beat.
export const TTL = 1000;
const MAX_MEMO_ENTRIES = 50_000;
const memo = new Map();
// A `stale` check lets a derived answer reject itself early: the memo's
// clock says fresh, but the files it was built from have moved on.
export function memoize(key, compute, stale) {
    const hit = memo.get(key);
    const now = Date.now();
    if (hit && now - hit.at < TTL && !stale?.(hit.value)) {
        return hit.value;
    }
    const value = compute();
    if (memo.size >= MAX_MEMO_ENTRIES && !memo.has(key)) {
        for (const oldest of memo.keys()) {
            memo.delete(oldest);
            if (memo.size <= MAX_MEMO_ENTRIES * 0.75) {
                break;
            }
        }
    }
    memo.set(key, { at: now, value });
    return value;
}
export function isFile(p) {
    return memoize(`file:${p}`, () => {
        try {
            return fs.statSync(p).isFile();
        } catch {
            return false;
        }
    });
}
export function mtimeOf(p) {
    return memoize(`mtime:${p}`, () => {
        try {
            return fs.statSync(p).mtimeMs;
        } catch {
            return null;
        }
    });
}
