// Spelling distance for "did you mean". A transposition counts as one
// edit, since "foucs" is one slip away from "focus".
export function editDistance(a, b) {
    const rows = a.length + 1;
    const cols = b.length + 1;
    const d = Array.from({ length: rows }, (_, i) => {
        const row = new Array(cols).fill(0);
        row[0] = i;
        return row;
    });
    for (let j = 0; j < cols; j++) d[0][j] = j;
    for (let i = 1; i < rows; i++) {
        for (let j = 1; j < cols; j++) {
            const cost = a[i - 1] === b[j - 1] ? 0 : 1;
            d[i][j] = Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost);
            if (i > 1 && j > 1 && a[i - 1] === b[j - 2] && a[i - 2] === b[j - 1]) {
                d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
            }
        }
    }
    return d[rows - 1][cols - 1];
}
export function didYouMean(value, candidates, budget = value.length < 6 ? 1 : 2) {
    if (value.length < 3) return null;
    let best = null;
    for (const name of candidates) {
        if (name === value) return null;
        if (Math.abs(name.length - value.length) > budget) continue;
        const distance = editDistance(value, name);
        if (distance > budget) continue;
        if (!best || distance < best.distance || (distance === best.distance && name.localeCompare(best.name) < 0)) {
            best = { name, distance };
        }
    }
    return best?.name ?? null;
}
