import MarkdownIt, { type Token } from 'markdown-it';
import MarkdownItKatex from '@vscode/markdown-it-katex';
import { PatternMatch, ReferenceType } from 'app/communication/communication.util';

// Metadata is owned by the posting_reference rule; other Markdown tokens never enter the reader below.
type ReferenceMetadata = PatternMatch & Record<string, unknown>;

// Retain the existing reference wire grammar. A sticky match starts only where the Markdown parser is currently reading.
const referencePattern =
    /(?<POST>#\d+)|(?<PROGRAMMING>\[programming].*?\[\/programming])|(?<MODELING>\[modeling].*?\[\/modeling])|(?<QUIZ>\[quiz].*?\[\/quiz])|(?<TEXT>\[text].*?\[\/text])|(?<FILE_UPLOAD>\[file-upload].*?\[\/file-upload])|(?<LECTURE>\[lecture].*?\[\/lecture])|(?<ATTACHMENT>\[attachment].*?\[\/attachment])|(?<ATTACHMENT_UNITS>\[lecture-unit].*?\[\/lecture-unit])|(?<SLIDE>\[slide].*?\[\/slide])|(?<USER>\[user].*?\[\/user])|(?<CHANNEL>\[channel].*?\[\/channel])|(?<IMAGE>!\[.*?]\(.*?\))|(?<FAQ>\[faq].*?\[\/faq])/y;

// Block parsing supplies source line ranges; inline parsing consumes escapes, entities and code before they can become references.
const markdown = MarkdownIt({ html: true, linkify: true }).use(MarkdownItKatex, { enableMathInlineInHtml: true });
// Include overlapping candidates: escaping an outer reference can leave an inner numeric reference editable.
const candidatesPattern = new RegExp('(?=(' + referencePattern.source + '))', 'g');
type SourceRange = { start: number; end: number };
markdown.inline.ruler.before('text', 'posting_reference', (state, silent) => {
    referencePattern.lastIndex = state.pos;
    const match = referencePattern.exec(state.src);
    if (!match) return false;
    const previous = state.tokens.at(-1);
    const followsAmpersand = state.src[state.pos - 1] === '&' || (!state.pending && previous?.type === 'text_special' && previous.info === 'entity' && previous.content === '&');
    if (match.groups?.POST && followsAmpersand) return false;
    const key = Object.keys(match.groups ?? {}).find((key) => match.groups?.[key]);
    if (!key) return false;
    if (!silent) {
        const token = state.push('posting_reference', '', 0);
        token.meta = { startIndex: state.pos, endIndex: referencePattern.lastIndex, referenceType: ReferenceType[key as keyof typeof ReferenceType] } satisfies PatternMatch;
    }
    state.pos = referencePattern.lastIndex;
    return true;
});

/** Finds references outside literal Markdown contexts and retains exact UTF-16 indices in the original wire content. */
export function postingReferenceMatches(source: string): PatternMatch[] {
    if (!source) return [];
    const lines = [0, ...Array.from(source.matchAll(/\r\n?|\n/g), (match) => match.index + match[0].length), source.length];
    const matches: PatternMatch[] = [];
    let row: { cells: SourceRange[]; inline: Token[] } | undefined;
    for (const block of markdown.parse(source, {})) {
        if (block.type === 'tr_open' && block.map) {
            row = { cells: tableCellRanges(source, lines[block.map[0]], lines[block.map[1]]), inline: [] };
        } else if (block.type === 'tr_close' && row) {
            appendSourceMatches(source, row.cells, row.inline, matches);
            row = undefined;
        } else if (block.type === 'inline') {
            if (row) row.inline.push(block);
            else if (block.map) appendSourceMatches(source, [{ start: lines[block.map[0]], end: lines[block.map[1]] }], [block], matches);
        }
    }
    return matches;
}

/**
 * Markdown removes container prefixes, normalizes line endings and unescapes table pipes before inline parsing.
 * These transformations preserve the order of wire-reference candidates. Pair every candidate (including literal ones)
 * with its source occurrence, then emit only the positions accepted by the parser. Repeated references inside code
 * therefore cannot shift a later real reference. Table cells are scanned independently, as in Markdown's table grammar.
 */
function appendSourceMatches(source: string, ranges: SourceRange[], inline: Token[], matches: PatternMatch[]): void {
    const originals = ranges.flatMap(({ start, end }) =>
        Array.from(source.slice(start, end).matchAll(candidatesPattern), (match) => ({ start: start + match.index, end: start + match.index + match[1].length })),
    );
    let ordinal = 0;
    for (const token of inline) {
        const accepted = new Map(
            (token.children ?? [])
                .filter((child) => child.type === 'posting_reference')
                .map((child) => {
                    const metadata = child.meta as ReferenceMetadata;
                    return [metadata.startIndex, metadata] as const;
                }),
        );
        for (const candidate of token.content.matchAll(candidatesPattern)) {
            const original = originals[ordinal++];
            const reference = accepted.get(candidate.index);
            if (original && reference) matches.push({ startIndex: original.start, endIndex: original.end, referenceType: reference.referenceType });
        }
    }
}

/** Table pipes delimit independent inline contexts, even when a code delimiter is unmatched in a cell. */
function tableCellRanges(source: string, start: number, end: number): { start: number; end: number }[] {
    const cells: { start: number; end: number }[] = [];
    const base = start;
    for (const match of source.slice(start, end).matchAll(/(?<!\\)\|/g)) {
        const boundary = match.index + base;
        cells.push({ start, end: boundary });
        start = boundary + 1;
    }
    cells.push({ start, end });
    return cells;
}
