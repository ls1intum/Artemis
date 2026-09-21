/**
 * Inline citation markers for the Iris global-search answer.
 *
 * The answer arrives as markdown containing sentence-level markers like `[2]`
 * (already sanitized and renumbered server-side to index the returned sources
 * list). This util converts EACH marker into its own small `<sup>` chip element
 * before markdown rendering; the markdown pipeline keeps inline HTML
 * (`html: true`) and DOMPurify keeps `sup`, `class`, `data-*`, `role` and
 * `tabindex` attributes, so the chip survives sanitization. `role="link"` and
 * `tabindex="0"` make each chip keyboard-focusable with Tab and activatable
 * with Enter/Space (see onAnswerClick/onAnswerKeydown), matching what a mouse
 * click already does. An answer without markers passes through unchanged,
 * which keeps old-server responses rendering exactly as before.
 *
 * A run of consecutive markers stays one chip per source rather than a single
 * combined chip: a combined chip is one hover target and one link for several
 * sources, so it cannot say what source 2 alone supports and its click can only
 * ever open the first of them. The run still reads as a group because
 * `.iris-cite + .iris-cite` tightens the spacing between adjacent chips.
 */

/** One or more consecutive `[n]` markers, treated as a single citation run. */
const MARKER_RUN_REGEX = /(?:\[\d+\]){1,}/g;

/** Digits of one marker inside a run. */
const SINGLE_MARKER_REGEX = /\[(\d+)\]/g;

/**
 * Every CommonMark code node this answer's markdown could realistically contain, tried longest-and-most-
 * specific first: a backtick fence of 3+ backticks or a tilde fence (incl. language tag, possibly
 * spanning lines), or an inline code span delimited by a run of one or more backticks closed by a
 * same-length run. CommonMark allows a longer run specifically so the span can contain a literal
 * backtick, e.g. ``a ` b`` uses two backticks as delimiters, and a span can itself contain a single line
 * break (CommonMark folds it to a space at render time) — matching only a single, same-line backtick
 * would stop at the interior backtick or newline and leak the rest as prose. The span's content may
 * cross a line break but not a full blank line: CommonMark inline parsing never crosses a paragraph
 * boundary either, and bounding it here keeps one stray unclosed backtick from silently swallowing
 * every following paragraph as "code" while still catching the real, useful case (a code term wrapped
 * mid-sentence). Content matched here is left untouched: a bracketed expression like an array index
 * (`list[0]`) is common in course content and must render as code, not as a citation chip.
 *
 * A fence's closing delimiter must be a LEGAL closing-fence line: a run of the fence character AT LEAST
 * as long as the opening run (CommonMark permits a longer closer, e.g. a ~~~ fence closed by ~~~~),
 * alone on its line (only leading indentation and trailing whitespace allowed) — not merely the same
 * characters appearing anywhere later in the content. Without the line-alone requirement, a fence whose
 * content itself contains the delimiter mid-line (e.g. a quoted string literal like `"```"` inside a
 * code sample) would close early and leak the rest of the real fenced block as prose; without the
 * at-least-as-long requirement, a valid longer closer is not recognized as closing the fence at all.
 *
 * Indented code blocks (no delimiter) are matched too, bounded to a run of such lines that starts at the
 * very beginning of the answer or right after a blank line — the same structural rule CommonMark itself
 * uses to tell a real indented code block from a paragraph or list item's continuation line. This is not
 * full list-nesting awareness (a full block parser's job), but the answer prompt only ever instructs flat,
 * single-line list items (`global_search_prompts.py`: "Use \n for new list items"), so a genuine
 * multi-line list continuation is not a realistic shape for this scan to misfire on.
 *
 * "Indented" is CommonMark's column rule, not a raw character count: a line indented by one tab (which
 * advances to the next 4-column stop, so it never needs 4 of them) counts exactly the same as one indented
 * by 4 spaces. And the blank line separating a code block from what comes before can itself carry
 * trailing whitespace — still blank, so it must not be required to be a bare `\n\n`.
 */
const CODE_SEGMENT_REGEX =
    /(`{3,})[\s\S]*?\n[ ]{0,3}\1`*[ \t]*(?=\n|$)|(~~~+)[\s\S]*?\n[ ]{0,3}\2~*[ \t]*(?=\n|$)|(`+)(?:(?!\n[ \t]*\n)[\s\S])*?\3(?!`)|(?:^|\n[ \t]*\n)(?:[ ]{4,}|[ ]{0,3}\t)[^\n]*(?:\n(?:[ ]{4,}|[ ]{0,3}\t)[^\n]*)*/g;

export interface CitationRenderResult {
    /** The answer markdown with marker runs replaced by `<sup>` chip elements. */
    html: string | undefined;
    /** All source numbers (1-based) cited anywhere in the answer. */
    citedNumbers: ReadonlySet<number>;
}

/**
 * Replaces citation markers with one superscript chip element each.
 *
 * Markers outside `1..sourceCount` are dropped defensively (the server already
 * strips them, but the client must not trust that); a run left empty after
 * filtering disappears entirely. Repeats inside one run collapse to a single
 * chip, so `[1][1]` does not render the same source twice.
 *
 * @param answer the answer markdown as received from the server
 * @param sourceCount the number of sources the markers may index into
 */
export function renderCitationMarkers(answer: string | undefined, sourceCount: number): CitationRenderResult {
    if (!answer || sourceCount <= 0) {
        return { html: answer, citedNumbers: new Set() };
    }
    const cited = new Set<number>();
    const replaceMarkers = (prose: string): string =>
        prose.replace(MARKER_RUN_REGEX, (run) => {
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
            return numbers.map((n) => `<sup class="iris-cite" data-n="${n}" role="link" tabindex="0">${n}</sup>`).join('');
        });

    // Walk the code segments in order, replacing markers only in the prose between them; code
    // segments themselves (and any bracketed text inside them) pass through unchanged.
    let html = '';
    let cursor = 0;
    for (const match of answer.matchAll(CODE_SEGMENT_REGEX)) {
        const index = match.index ?? 0;
        html += replaceMarkers(answer.slice(cursor, index));
        html += match[0];
        cursor = index + match[0].length;
    }
    html += replaceMarkers(answer.slice(cursor));
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
