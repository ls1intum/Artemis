import MarkdownIt, { type Token } from 'markdown-it';

/** A source range that an inline rule consumed, relative to the text of the inline token it was parsed from. */
interface InlineRange {
    source: string;
    start: number;
    end: number;
    /** Set for the opening and closing tag of an HTML anchor, whose label lies between them. */
    anchor?: 'open' | 'close';
}

/** Inline rules whose matches are code spans, links, images or autolinks. */
const LINK_OR_CODE_RULES = ['backticks', 'link', 'image', 'autolink'];

/** Tags whose raw HTML block lasts until the matching closing tag; CommonMark's first kind of HTML block. */
const RAW_TEXT_TAGS = ['pre', 'script', 'style', 'textarea'];

/** Tags that start a raw HTML block lasting until a blank line, even in the middle of a paragraph; CommonMark's sixth kind of HTML block. */
const BLOCK_TAGS = new Set([
    'address',
    'article',
    'aside',
    'base',
    'basefont',
    'blockquote',
    'body',
    'caption',
    'center',
    'col',
    'colgroup',
    'dd',
    'details',
    'dialog',
    'dir',
    'div',
    'dl',
    'dt',
    'fieldset',
    'figcaption',
    'figure',
    'footer',
    'form',
    'frame',
    'frameset',
    'h1',
    'h2',
    'h3',
    'h4',
    'h5',
    'h6',
    'head',
    'header',
    'hr',
    'html',
    'iframe',
    'legend',
    'li',
    'link',
    'main',
    'menu',
    'menuitem',
    'nav',
    'noframes',
    'ol',
    'optgroup',
    'option',
    'p',
    'param',
    'search',
    'section',
    'summary',
    'table',
    'tbody',
    'td',
    'tfoot',
    'th',
    'thead',
    'title',
    'tr',
    'track',
    'ul',
]);

function isAsciiLetter(character: string | undefined): boolean {
    return !!character && ((character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z'));
}

function isTagNameCharacter(character: string | undefined): boolean {
    return isAsciiLetter(character) || (!!character && character >= '0' && character <= '9') || character === '-';
}

/**
 * Records a range in the parser environment, which collects them for {@link isInLinkOrCode}.
 * @param env The parser environment.
 * @param range The range to record.
 */
function recordRange(env: Record<string | symbol, unknown>, range: InlineRange): void {
    const inlineRanges = env.inlineRanges;
    if (Array.isArray(inlineRanges)) {
        inlineRanges.push(range);
    }
}

/**
 * Reads an HTML tag such as `<a href="x">` or `</a>` that starts at the given position, scanning character by character. Quoted attribute
 * values may contain `>`.
 * @param source The text to read from.
 * @param start The position of the `<`.
 * @return the end of the tag (exclusive), its lower-case name and whether it is a closing tag, or undefined if no tag starts here
 */
function readHtmlTag(source: string, start: number): { end: number; name: string; closing: boolean } | undefined {
    if (source[start] !== '<') {
        return undefined;
    }
    let position = start + 1;
    const closing = source[position] === '/';
    if (closing) {
        position++;
    }
    const nameStart = position;
    if (!isAsciiLetter(source[position])) {
        return undefined;
    }
    while (position < source.length && isTagNameCharacter(source[position])) {
        position++;
    }
    const name = source.slice(nameStart, position).toLowerCase();
    let quote: string | undefined;
    for (; position < source.length; position++) {
        const character = source[position];
        if (quote) {
            quote = character === quote ? undefined : quote;
        } else if (character === '"' || character === "'") {
            quote = character;
        } else if (character === '<') {
            return undefined;
        } else if (character === '>') {
            return { end: position + 1, name, closing };
        }
    }
    return undefined;
}

/**
 * A CommonMark parser, only used to find the links and code around a selection; its result is never rendered. Markdown-it reports source
 * lines for blocks but no columns for inline tokens, so the inline rules for links and code are wrapped to record the range of text each
 * match consumed. HTML stays disabled: an own rule only records where HTML tags are and keeps them as plain text.
 */
const markdownParser = MarkdownIt();
for (const name of LINK_OR_CODE_RULES) {
    const rule = markdownParser.inline.ruler.__rules__.find((candidate) => candidate.name === name)?.fn;
    if (rule) {
        markdownParser.inline.ruler.at(name, (state, silent) => {
            const start = state.pos;
            const matched = rule(state, silent);
            if (matched && !silent) {
                recordRange(state.env, { source: state.src, start, end: state.pos });
            }
            return matched;
        });
    }
}
markdownParser.inline.ruler.before('html_inline', 'html_tag_range', (state, silent) => {
    const tag = readHtmlTag(state.src, state.pos);
    if (!tag) {
        return false;
    }
    if (!silent) {
        state.pending += state.src.slice(state.pos, tag.end);
        const anchor = tag.name === 'a' ? (tag.closing ? 'close' : 'open') : undefined;
        recordRange(state.env, { source: state.src, start: state.pos, end: tag.end, anchor });
    }
    state.pos = tag.end;
    return true;
});

/**
 * Adds the range from each HTML anchor's opening to its closing tag, so its label counts as part of a link. An anchor that is not closed
 * reaches to the end of the text.
 * @param ranges The ranges recorded for one inline token's text.
 * @param sourceLength The length of that text.
 * @return the given ranges and the anchor labels
 */
function withAnchorLabels(ranges: InlineRange[], sourceLength: number): InlineRange[] {
    const openAnchors: InlineRange[] = [];
    const labels: InlineRange[] = [];
    for (const range of [...ranges].sort((first, second) => first.start - second.start)) {
        if (range.anchor === 'open') {
            openAnchors.push(range);
        } else if (range.anchor === 'close') {
            const open = openAnchors.pop();
            if (open) {
                labels.push({ source: range.source, start: open.start, end: range.end });
            }
        }
    }
    const unclosed = openAnchors.map((open) => ({ source: open.source, start: open.start, end: sourceLength }));
    return [...ranges, ...labels, ...unclosed];
}

/**
 * Recognizes the start of a raw HTML block, following CommonMark's seven kinds of HTML block.
 * @param text The line without its indentation and container markers, such as block quote or list markers.
 * @return how the block ends (a check that includes the start line) and whether it may interrupt a paragraph, or undefined if none starts
 */
function htmlBlockKind(text: string): { endsOn: (candidate: string) => boolean; canInterruptParagraph: boolean } | undefined {
    if (!text.startsWith('<')) {
        return undefined;
    }
    const lower = text.toLowerCase();
    const closing = text[1] === '/';
    let nameEnd = closing ? 2 : 1;
    while (isTagNameCharacter(text[nameEnd])) {
        nameEnd++;
    }
    const name = isAsciiLetter(text[closing ? 2 : 1]) ? lower.slice(closing ? 2 : 1, nameEnd) : '';
    const afterName = text[nameEnd];
    const nameEndsCleanly = afterName === undefined || afterName === ' ' || afterName === '\t' || afterName === '>' || text.startsWith('/>', nameEnd);
    const endsWith = (terminator: string) => ({ endsOn: (candidate: string) => candidate.toLowerCase().includes(terminator), canInterruptParagraph: true });
    if (!closing && RAW_TEXT_TAGS.includes(name) && afterName !== '/' && nameEndsCleanly) {
        return { endsOn: (candidate) => RAW_TEXT_TAGS.some((rawTag) => candidate.toLowerCase().includes(`</${rawTag}>`)), canInterruptParagraph: true };
    }
    if (lower.startsWith('<!--')) {
        return endsWith('-->');
    }
    if (lower.startsWith('<?')) {
        return endsWith('?>');
    }
    if (lower.startsWith('<![cdata[')) {
        return endsWith(']]>');
    }
    if (lower.startsWith('<!') && isAsciiLetter(text[2])) {
        return endsWith('>');
    }
    const untilBlankLine = (candidate: string) => !candidate.trim();
    if (BLOCK_TAGS.has(name) && nameEndsCleanly) {
        return { endsOn: untilBlankLine, canInterruptParagraph: true };
    }
    const tag = readHtmlTag(text, 0);
    if (tag && !RAW_TEXT_TAGS.includes(name) && !text.slice(tag.end).trim()) {
        return { endsOn: untilBlankLine, canInterruptParagraph: false };
    }
    return undefined;
}

/**
 * Records raw HTML blocks as `html_block_range` tokens with their source lines, in place of markdown-it's own HTML block rule, which stays
 * disabled. Running as a block rule, it sees each line without its block quote and list markers, and it consumes the block up to its own
 * end, so lines inside it never become code blocks or paragraphs. Mirrors the structure of markdown-it's `html_block` rule.
 */
markdownParser.block.ruler.before(
    'html_block',
    'html_block_range',
    (state, startLine, endLine, silent) => {
        if (state.sCount[startLine] - state.blkIndent >= 4) {
            return false;
        }
        const lineText = (line: number) => state.src.slice(state.bMarks[line] + state.tShift[line], state.eMarks[line]);
        const kind = htmlBlockKind(lineText(startLine));
        if (!kind) {
            return false;
        }
        if (silent) {
            // asked whether the block may interrupt a paragraph
            return kind.canInterruptParagraph;
        }
        let nextLine = startLine + 1;
        if (!kind.endsOn(lineText(startLine))) {
            for (; nextLine < endLine && state.sCount[nextLine] >= state.blkIndent; nextLine++) {
                const text = lineText(nextLine);
                if (kind.endsOn(text)) {
                    // a blank line ends a block without belonging to it
                    nextLine += text.trim() ? 1 : 0;
                    break;
                }
            }
        }
        state.line = nextLine;
        state.push('html_block_range', '', 0).map = [startLine, nextLine];
        return true;
    },
    { alt: ['paragraph', 'reference', 'blockquote'] },
);

/**
 * Splits a table row into the source columns of its cells, like markdown-it: cells are separated by pipes that are not escaped with a
 * backslash, and a pipe at the start or end of the row only closes the row.
 * @param line The source line of the row.
 * @return the start (inclusive) and end (exclusive) column of each cell
 */
function tableCellColumns(line: string): [number, number][] {
    const pipes: number[] = [];
    for (let index = 0; index < line.length; index++) {
        if (line[index] === '\\') {
            index++;
        } else if (line[index] === '|') {
            pipes.push(index);
        }
    }
    const bounds = [-1, ...pipes, line.length];
    const cells = bounds.slice(1).map((end, index): [number, number] => [bounds[index] + 1, end]);
    if (pipes.length > 0 && !line.slice(0, pipes[0]).trim()) {
        cells.shift();
    }
    if (pipes.length > 0 && !line.slice(pipes[pipes.length - 1] + 1).trim()) {
        cells.pop();
    }
    return cells;
}

/**
 * Maps a column of a source line to the matching offset in a text that the line contains from {@code contentStart} on, with escaped
 * pipes reduced to plain pipes as in table cells.
 */
function toContentOffset(line: string, contentStart: number, column: number): number {
    let offset = 0;
    for (let index = contentStart; index < column; index++) {
        offset += line[index] === '\\' && line[index + 1] === '|' ? 0 : 1;
    }
    return offset;
}

/** Where the selected text lies in the text of an inline token. */
interface ContentSelection {
    content: string;
    start: number;
    end: number;
}

/**
 * Checks whether a selection on one line lies where a pasted link would not work: inside or across an existing link (its label, or its
 * destination such as the placeholder the link button selects), an image, an autolink, an HTML tag or anchor, inline code, inside a fenced
 * or indented code block or a raw HTML block (also inside block quotes and lists), or anywhere outside paragraph, heading and table text, such as a reference definition or a
 * list marker. The unchanged document is parsed with the same CommonMark rules as the preview, so references and code spans over several
 * lines resolve as there.
 * @param lines The lines of the document.
 * @param lineIndex The index of the line with the selection.
 * @param startIndex The column where the selection starts, counted from 0.
 * @param endIndex The column where the selection ends (exclusive).
 * @return true if a pasted URL must not be turned into a link around the selection
 */
export function isInLinkOrCode(lines: string[], lineIndex: number, startIndex: number, endIndex: number): boolean {
    const inlineRanges: InlineRange[] = [];
    const tokens = markdownParser.parse(lines.join('\n'), { inlineRanges });
    const coversLine = (map: [number, number] | null) => !!map && map[0] <= lineIndex && lineIndex < map[1];

    if (tokens.some((token) => ['fence', 'code_block', 'html_block_range'].includes(token.type) && coversLine(token.map))) {
        return true;
    }
    const selection = findContentSelection(tokens, lines[lineIndex] ?? '', lineIndex, startIndex, endIndex, coversLine);
    if (!selection) {
        // The selection is not inside the text of a paragraph, heading or table cell, so the paste is left to the editor.
        return true;
    }
    const ranges = withAnchorLabels(
        inlineRanges.filter((range) => range.source === selection.content),
        selection.content.length,
    );
    return ranges.some((range) => range.start < selection.end && selection.start < range.end);
}

/**
 * Finds the inline token whose text contains the selection and the selection's offsets in that text.
 * @return the token's text with the selection's offsets, or undefined if the selection is not inside paragraph, heading or table cell text
 */
function findContentSelection(
    tokens: Token[],
    line: string,
    lineIndex: number,
    startIndex: number,
    endIndex: number,
    coversLine: (map: [number, number] | null) => boolean,
): ContentSelection | undefined {
    for (let index = 0; index < tokens.length; index++) {
        const token = tokens[index];
        if (token.type === 'tr_open' && coversLine(token.map)) {
            // Table cells carry no source lines of their own, so they are matched with the cells of the row's source line.
            const cells = [];
            for (let cellIndex = index + 1; cellIndex < tokens.length && tokens[cellIndex].type !== 'tr_close'; cellIndex++) {
                if (tokens[cellIndex].type === 'inline') {
                    cells.push(tokens[cellIndex]);
                }
            }
            const columns = tableCellColumns(line);
            const selected = columns.findIndex(([start, end]) => start <= startIndex && endIndex <= end);
            const cell = cells[selected];
            if (!cell) {
                return undefined;
            }
            const raw = line.slice(columns[selected][0], columns[selected][1]);
            const contentStart = columns[selected][0] + raw.length - raw.trimStart().length;
            if (raw.trim().replaceAll('\\|', '|') !== cell.content || startIndex < contentStart) {
                return undefined;
            }
            return { content: cell.content, start: toContentOffset(line, contentStart, startIndex), end: toContentOffset(line, contentStart, endIndex) };
        }
        if (token.type !== 'inline' || !token.map || !coversLine(token.map)) {
            continue;
        }
        const contentLines = token.content.split('\n');
        const contentLineIndex = lineIndex - token.map[0];
        const contentLine = contentLines[contentLineIndex];
        if (contentLine === undefined) {
            // the underline of a heading, which belongs to the heading's lines but not to its text
            continue;
        }
        // The text of a paragraph or heading line is the end of its source line, after any indentation, list or quote markers.
        const contentStart = line.lastIndexOf(contentLine);
        if (contentStart === -1 || startIndex < contentStart || contentStart + contentLine.length < endIndex) {
            return undefined;
        }
        const lineOffset = contentLines.slice(0, contentLineIndex).reduce((offset, contentText) => offset + contentText.length + 1, 0);
        return { content: token.content, start: lineOffset + startIndex - contentStart, end: lineOffset + endIndex - contentStart };
    }
    return undefined;
}
