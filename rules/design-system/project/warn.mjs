import console from 'node:console';

const seen = new Set();

/** Report configuration problems once per subject, not once per template node. */
export function warnOnce(key, message) {
    if (seen.has(key)) return;
    seen.add(key);
    console.warn(`[design-system] ${message}`);
}
