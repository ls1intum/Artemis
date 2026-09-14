/**
 * Inline citation markers for the Iris global-search answer.
 *
 * The answer arrives as markdown containing sentence-level markers like `[2]`
 * (already sanitized and renumbered server-side to index the returned sources
 * list). This util converts each RUN of consecutive markers into one small
 * `<sup>` chip element before markdown rendering; the markdown pipeline keeps
 * inline HTML (`html: true`) and DOMPurify keeps `sup`, `class` and `data-*`
 * attributes, so the chip survives sanitization. An answer without markers
 * passes through unchanged, which keeps old-server responses rendering
 * exactly as before.
 */

/** One or more consecutive `[n]` markers, treated as a single citation run. */
const MARKER_RUN_REGEX = /(?:\[\d+\]){1,}/g;

/** Digits of one marker inside a run. */
const SINGLE_MARKER_REGEX = /\[(\d+)\]/g;

export interface CitationRenderResult {
    /** The answer markdown with marker runs replaced by `<sup>` chip elements. */
    html: string | undefined;
    /** All source numbers (1-based) cited anywhere in the answer. */
    citedNumbers: ReadonlySet<number>;
}

/**
 * Replaces citation marker runs with superscript chip elements.
 *
 * Markers outside `1..sourceCount` are dropped defensively (the server already
 * strips them, but the client must not trust that); a run left empty after
 * filtering disappears entirely.
 *
 * @param answer the answer markdown as received from the server
 * @param sourceCount the number of sources the markers may index into
 */
export function renderCitationMarkers(answer: string | undefined, sourceCount: number): CitationRenderResult {
    if (!answer || sourceCount <= 0) {
        return { html: answer, citedNumbers: new Set() };
    }
    const cited = new Set<number>();
    const html = answer.replace(MARKER_RUN_REGEX, (run) => {
        const numbers: number[] = [];
        for (const match of run.matchAll(SINGLE_MARKER_REGEX)) {
            const value = Number(match[1]);
            if (value >= 1 && value <= sourceCount && !numbers.includes(value)) {
                numbers.push(value);
            }
        }
        if (numbers.length === 0) {
            return '';
        }
        numbers.forEach((n) => cited.add(n));
        return `<sup class="iris-cite" data-n="${numbers.join(' ')}">${numbers.join(',')}</sup>`;
    });
    return { html, citedNumbers: cited };
}

/** Parses the `data-n` attribute of a citation chip element back into numbers. */
export function parseCitationNumbers(dataN: string | undefined): number[] {
    if (!dataN) {
        return [];
    }
    return dataN
        .split(' ')
        .map(Number)
        .filter((n) => Number.isInteger(n) && n >= 1);
}
