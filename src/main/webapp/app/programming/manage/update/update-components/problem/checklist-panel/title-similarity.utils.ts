/**
 * Utility functions for fuzzy competency title matching.
 * Used by the checklist panel to match AI-inferred competency titles
 * against existing course competencies.
 */

/**
 * Lightweight suffix-stripping stemmer for competency title matching.
 * Reduces common English morphological variants to a shared root
 * (e.g., "algorithms" → "algorithm", "sorting" → "sort", "implementation" → "implement").
 */
export function stemWord(word: string): string {
    if (word.length <= 3) return word;

    // Order matters: try longest suffixes first
    const suffixes = [
        'isation',
        'ization',
        'ation',
        'tion',
        'sion',
        'ment',
        'ness',
        'ence',
        'ance',
        'ible',
        'able',
        'ity',
        'ing',
        'ies',
        'ous',
        'ive',
        'ed',
        'ly',
        'er',
        'es',
        's',
    ];

    for (const suffix of suffixes) {
        if (word.endsWith(suffix) && word.length - suffix.length >= 3) {
            return word.slice(0, -suffix.length);
        }
    }
    return word;
}

const STOP_WORDS = new Set(['a', 'an', 'the', 'and', 'or', 'of', 'in', 'on', 'for', 'to', 'with', 'by', 'is', 'are', 'as', 'at', 'its']);

/**
 * Tokenizes and stems a title string into a sorted array of normalized word roots.
 * Filters out common stop words that do not contribute to semantic matching.
 */
export function tokenizeAndStem(title: string): string[] {
    return title
        .split(/\s+/)
        .filter(Boolean)
        .filter((w) => !STOP_WORDS.has(w))
        .map((w) => stemWord(w))
        .sort();
}

/**
 * Computes the Damerau-Levenshtein distance between two strings.
 * Accounts for insertions, deletions, substitutions, and transpositions of adjacent characters.
 */
export function damerauLevenshtein(a: string, b: string): number {
    const lenA = a.length;
    const lenB = b.length;

    if (lenA === 0) return lenB;
    if (lenB === 0) return lenA;

    // Create distance matrix (lenA+1) x (lenB+1)
    const d: number[][] = Array.from({ length: lenA + 1 }, () => new Array<number>(lenB + 1).fill(0));

    for (let i = 0; i <= lenA; i++) d[i][0] = i;
    for (let j = 0; j <= lenB; j++) d[0][j] = j;

    for (let i = 1; i <= lenA; i++) {
        for (let j = 1; j <= lenB; j++) {
            const cost = a[i - 1] === b[j - 1] ? 0 : 1;
            d[i][j] = Math.min(
                d[i - 1][j] + 1, // deletion
                d[i][j - 1] + 1, // insertion
                d[i - 1][j - 1] + cost, // substitution
            );
            // transposition
            if (i > 1 && j > 1 && a[i - 1] === b[j - 2] && a[i - 2] === b[j - 1]) {
                d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + cost);
            }
        }
    }

    return d[lenA][lenB];
}

/**
 * Computes a similarity score between two competency titles.
 * Returns a value between 0.0 (no overlap) and 1.0 (identical).
 *
 * Uses Damerau-Levenshtein distance on stemmed, stop-word-filtered,
 * sorted token strings. This handles typos, word reordering, morphological
 * variants, and stop-word differences in a single unified metric.
 */
export function titleSimilarity(a: string, b: string): number {
    if (!a || !b) return 0;
    if (a === b) return 1.0;

    // One completely contains the other → high match
    if (a.includes(b) || b.includes(a)) return 0.85;

    // Normalize: stem, remove stop words, sort to be order-independent
    const normA = tokenizeAndStem(a).join(' ');
    const normB = tokenizeAndStem(b).join(' ');

    if (normA.length === 0 || normB.length === 0) return 0;
    if (normA === normB) return 1.0;

    const dist = damerauLevenshtein(normA, normB);
    const maxLen = Math.max(normA.length, normB.length);

    return 1 - dist / maxLen;
}
