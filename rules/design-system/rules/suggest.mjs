// Naming the fix. A message that lists twelve tokens leaves the search to
// the reader; one that names the nearest two closes it.
import { colorDistance, parseColor } from '../grammar/colors.mjs';
import { formatPx } from '../grammar/lengths.mjs';
import { didYouMean } from '../grammar/similar.mjs';
import { PALETTE } from '../grammar/tailwind-theme.mjs';
export { didYouMean };
// Beyond this, a suggestion looks nothing like the color reached for.
export const COLOR_THRESHOLD = 0.12;
// Tie order among tokens with the same value.
const PRIORITY = [
    'background',
    'foreground',
    'muted',
    'muted-foreground',
    'primary',
    'primary-foreground',
    'secondary',
    'secondary-foreground',
    'accent',
    'accent-foreground',
    'destructive',
    'destructive-foreground',
    'border',
    'input',
    'ring',
    'card',
    'card-foreground',
    'popover',
    'popover-foreground',
];
const priorityOf = (name) => {
    const index = PRIORITY.indexOf(name);
    return index === -1 ? PRIORITY.length : index;
};
export function roleOf(prefix) {
    return /^(?:text|placeholder|caret|decoration|fill|stroke)-$/.test(prefix) ? 'text' : 'surface';
}
const isForeground = (name) => name === 'foreground' || name.endsWith('-foreground');
// Tokens that share a value (secondary, muted and accent are one gray)
// collapse to one name, so the list is not three spellings of the same
// color. A surface is not offered -foreground tokens while a surface
// token is close; text may take either.
export function nearestColorTokens(lab, tokens, role, limit = 2) {
    const groups = new Map();
    for (const [name, value] of tokens) {
        const distance = colorDistance(lab, value);
        if (distance > COLOR_THRESHOLD) continue;
        const key = distance.toFixed(3);
        const group = groups.get(key);
        if (group) group.names.push(name);
        else groups.set(key, { distance, names: [name] });
    }
    const prefer = role === 'text' ? isForeground : (n) => !isForeground(n);
    let candidates = [...groups.values()]
        .sort((a, b) => a.distance - b.distance)
        .map(({ names }) => names.sort((a, b) => Number(prefer(b)) - Number(prefer(a)) || priorityOf(a) - priorityOf(b) || a.localeCompare(b))[0]);
    if (role === 'surface' && candidates.some((n) => !isForeground(n))) {
        candidates = candidates.filter((n) => !isForeground(n));
    }
    return candidates.slice(0, limit);
}
export function paletteColor(value) {
    const raw = PALETTE[value];
    return raw ? parseColor(raw) : null;
}
// A color as written inside brackets: "#333", "oklch(0.5_0.1_20)".
export function arbitraryColor(inner) {
    const text = inner.replace(/^color:/, '').replace(/_/g, ' ');
    return parseColor(text);
}
export function nearestSteps(px, scale, limit = 2) {
    return [...scale]
        .map(([name, value]) => ({
            name,
            px: value,
            exact: Math.abs(value - px) < 0.01,
        }))
        .sort((a, b) => Math.abs(a.px - px) - Math.abs(b.px - px) || a.px - b.px)
        .slice(0, limit);
}
export function formatSteps(steps, toClass) {
    return steps.map((s) => `${toClass(s.name)} (${formatPx(s.px)})`).join(', ');
}
