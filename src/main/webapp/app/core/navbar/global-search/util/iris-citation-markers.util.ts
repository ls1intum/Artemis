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
 * Every CommonMark code node this answer's markdown could realistically contain, plus KaTeX math (see
 * the last two alternatives below) — tried longest-and-most-specific first: a backtick fence of 3+
 * backticks or a tilde fence (incl. language tag, possibly
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
 * An inline span's delimiter, on either side, must be a COMPLETE backtick run — bounded by a non-backtick
 * character (or the string edge) — not merely a same-length run reachable by landing partway through a
 * longer one. A `\1`-style backreference alone only checks that the closer isn't followed by one more
 * backtick; it does not check what precedes it, so inside a 1-backtick span whose content itself contains
 * a 2-backtick run before the real content, e.g. `` `a``values[1]` ``, the SECOND backtick of that
 * internal run also satisfies "one backtick not followed by another" and closes the span early, leaking
 * `values[1]` after it as citable prose — even though CommonMark treats the internal 2-run as ordinary
 * content (it cannot close a 1-backtick opener) and keeps the whole thing one span. Guarding both the
 * opener and the closer with a same negative lookbehind rejects any backtick that is itself preceded by
 * another backtick, so only a run's true first (and, symmetrically, true last) character can start or end
 * a match.
 *
 * The opener's run length is captured ATOMICALLY — via a lookahead (`(?=(`+))` followed by a
 * backreference to consume it) rather than a plain `(`+)` — so a run that fails to find a matching-length
 * closer can never backtrack into trying a SHORTER prefix of that same run as a fresh opener. Without
 * this, e.g. `` Use ``Claim.[1]` after `` (a 2-backtick run with no later 2-backtick closer, followed by
 * an unrelated stray 1-backtick) would have a plain greedy `(`+)` fail to close as a 2-backtick span, then
 * backtrack to re-try the SAME position as a 1-backtick opener — which DOES find that later stray
 * backtick as a spurious closer, hiding `Claim.[1]` as "code" it was never meant to be. CommonMark itself
 * has no such retry: an opening run with no matching-length closer anywhere later is literal text, full
 * stop, and the parser resumes looking for a NEW, independent opener strictly after it — never by
 * re-reading part of the same run.
 *
 * A fence's closing delimiter must be a LEGAL closing-fence line: a run of the fence character AT LEAST
 * as long as the opening run (CommonMark permits a longer closer, e.g. a ~~~ fence closed by ~~~~),
 * alone on its line (only leading indentation and trailing whitespace allowed) — not merely the same
 * characters appearing anywhere later in the content. Without the line-alone requirement, a fence whose
 * content itself contains the delimiter mid-line (e.g. a quoted string literal like `"```"` inside a
 * code sample) would close early and leak the rest of the real fenced block as prose; without the
 * at-least-as-long requirement, a valid longer closer is not recognized as closing the fence at all.
 *
 * A fence with no legal closing line anywhere in the rest of the answer is still protected through the
 * end of the answer, since CommonMark treats end-of-document as an implicit close for an unterminated
 * fence. This is not an edge case here: the answer streams into the client sentence by sentence, so a
 * partial draft can legitimately contain an opening fence whose closer the model has not emitted yet.
 * Without this fallback, that in-progress code block's content (e.g. `values[1]`) would fall through to
 * the citation scan on every render until the closer finally arrives.
 *
 * A run of 3+ backticks or tildes only opens a FENCE at a legal fence-opening position: the very start
 * of the answer, or right after a newline, with up to 3 leading spaces (CommonMark allows the fence
 * character itself to sit up to 3 columns in; 4+ leading spaces makes it an indented code block instead,
 * which the last alternative below already covers). Anywhere else — most commonly a multi-backtick
 * inline span used mid-sentence, e.g. `` ```values[1]``` `` — a leading backtick run is NOT a fence
 * opener and must fall through to the inline-span alternative instead. This matters together with the
 * EOF fallback above: an unanchored fence alternative would misread a mid-line multi-backtick span as an
 * unterminated fence opener (no legal closing FENCE LINE ever follows one, since its own closer is
 * mid-line too) and, via that EOF fallback, swallow everything from the span to the end of the answer as
 * "code," dropping every real citation after it.
 *
 * A backtick fence's opening line may not itself contain a backtick after the opening run (CommonMark:
 * "If the info string comes after a backtick fence, it may not contain any backtick characters" — this
 * restriction does not apply to tilde fences). Without checking for one, a line-start triple-backtick
 * run whose own closer sits later on the SAME line, e.g. `` ```values[1]``` `` `, is misread as a fence
 * opener too: no legal closing FENCE LINE ever follows it either, so the same EOF fallback would swallow
 * everything after it. Requiring the rest of the opening line to be backtick-free sends that case to the
 * inline-span alternative instead, which closes correctly at the matching backtick run.
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
 *
 * The last two alternatives protect KaTeX math the same way, since the answer prompt's MATH section
 * (`global_search_prompts.py`) has the model wrap standalone and inline math alike in `$$...$$`/`$...$`,
 * and a bracketed numeric index is just as natural inside a formula (`$x[1]$`) as inside code — the
 * server's own citation-marker rule already treats `$$` as marker-adjacent punctuation for the same
 * reason (`_CITATION_MARKER_RE`'s `(?<=\$\$)` lookbehind). Display math (`$$...$$`) gets the SAME
 * unterminated-at-EOF fallback as a fence, and for the same reason: a streamed partial can legitimately
 * contain an opening `$$` whose closer has not arrived yet. Inline math (`$...$`) does NOT: unlike `$$`,
 * a single `$` is common in ordinary prose as plain currency ("$5 and $10"), so treating an unmatched
 * opener as protected-through-EOF would swallow every citation after an unrelated stray dollar sign.
 * Instead it requires an actual matching closer on the SAME line, with the standard convention that
 * rules out currency text: the opener must not be followed by whitespace and the closer must not be
 * preceded by it, which "$5 and $10" fails (the second `$` sits right after a space) and "$x[1]$" passes.
 */
const PROTECTED_SEGMENT_REGEX =
    /(?<=^|\n)[ ]{0,3}(`{3,})(?=[^`\n]*(?:\n|$))[\s\S]*?(?:\n[ ]{0,3}\1`*[ \t]*(?=\n|$)|$)|(?<=^|\n)[ ]{0,3}(~~~+)[\s\S]*?(?:\n[ ]{0,3}\2~*[ \t]*(?=\n|$)|$)|(?<!`)(?=(`+))\3(?:(?!\n[ \t]*\n)[\s\S])*?(?<!`)\3(?!`)|(?:^|\n[ \t]*\n)(?:[ ]{4,}|[ ]{0,3}\t)[^\n]*(?:\n(?:[ ]{4,}|[ ]{0,3}\t)[^\n]*)*|\$\$[\s\S]*?(?:\$\$|$)|\$(?!\$)(?!\s)[^\n$]*?(?<!\s)\$(?!\$)/g;

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

    // Walk the protected (code and math) segments in order, replacing markers only in the prose
    // between them; those segments themselves (and any bracketed text inside them) pass through unchanged.
    let html = '';
    let cursor = 0;
    for (const match of answer.matchAll(PROTECTED_SEGMENT_REGEX)) {
        const index = match.index ?? 0;
        html += replaceMarkers(answer.slice(cursor, index));
        html += match[0];
        cursor = index + match[0].length;
    }
    html += replaceMarkers(answer.slice(cursor));
    return { html, citedNumbers: cited };
}

/**
 * Whether `position` falls strictly inside a protected (code or math) segment of `text` — the same
 * boundary `renderCitationMarkers` uses to decide a `[n]` marker there is exempt from citation
 * replacement. Reused by the answer card's word-by-word reveal to keep the fade-tail `<span>` it
 * splices into the raw markdown source from landing inside one of those same segments, which would
 * corrupt it exactly the way an un-excluded citation marker would — a code span, fence, or KaTeX
 * formula treats an injected HTML tag as literal content rather than a real element. `position` is
 * safe at either edge of the segment (the tag then wraps the whole intact segment, or lands entirely
 * after it) and unsafe anywhere strictly between its opening and closing delimiters.
 */
export function isInsideProtectedSegment(text: string, position: number): boolean {
    for (const match of text.matchAll(PROTECTED_SEGMENT_REGEX)) {
        const start = match.index ?? 0;
        const end = start + match[0].length;
        if (start < position && position < end) {
            return true;
        }
    }
    return false;
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
