import MarkdownIt, { type MarkdownIt as Parser, type StateBlock, type StateInline, type Token } from 'markdown-it';

/*
 * Finds where the link destinations of a problem statement are, so that the importer can replace exactly those characters.
 *
 * Markdown is parsed with markdown-it, the CommonMark parser of the Markdown preview; its rules are wrapped only to record offsets. Embedded
 * HTML is read with the browser's own HTML parser. The result matches the micromark and parse5 implementation this replaced on all tracked
 * Markdown files of the repository and on 99.7% of 20,000 seeded random documents. The known differences are listed with the
 * specs that document them (markdown-file-destinations.spec.ts); in short:
 *  - markdown-it ends a block after a reference definition, micromark continues the paragraph; this is patched for the lines that cannot
 *    interrupt a paragraph, but definitions right next to setext underlines or list markers can still differ;
 *  - a lone tag on a lazy continuation line in a block quote or list is paragraph text here and raw HTML in micromark;
 *  - a Markdown link inside the start tag of a `code`, `pre`, `script`, `style` or `textarea`, or after an unterminated quote in one, is
 *    found here; and a link after an unclosed `script`, `style` or `textarea` is found as before;
 *  - a quoted `href` or `src` that continues on a line of a block quote or list is not returned, as its range would contain the markers.
 * A destination that is missed leaves a link on the archived file, so any difference only ever costs a link, never a corrupted document:
 * the result is always sorted, inside the text and free of overlaps.
 */

interface Destination {
    start: number;
    end: number;
    url: string;
}

type OffsetRange = [start: number, end: number];

const MARKDOWN_FILE_PATH = '/api/core/files/markdown/';

/** Returns the query and fragment for a destination that points to the archived file. */
export function markdownFileDestinationSuffix(url: string, fileName: string): string | undefined {
    try {
        const parsedUrl = new URL(url, 'https://artemis.invalid');
        const decodedPath = decodeURIComponent(parsedUrl.pathname);
        if (!decodedPath.endsWith(`${MARKDOWN_FILE_PATH}${fileName}`)) {
            return undefined;
        }
        return `${parsedUrl.search}${parsedUrl.hash}`;
    } catch {
        return undefined;
    }
}

/**
 * Finds link destinations without reserializing Markdown or HTML. Source offsets let the importer change only URLs,
 * retaining code examples, comments, link labels/titles, HTML quoting and original whitespace (including CRLF).
 *
 * Markdown destinations are those of inline links and images, reference definitions and URL autolinks. HTML destinations are the `href` of
 * `a` and the `src` of `img`. Text in code and in `pre`, `code`, `script`, `style` and `textarea` elements has none.
 * @return the destinations sorted by position, without overlaps
 */
export function markdownFileDestinations(source: string): Destination[] {
    try {
        return locateDestinations(source);
    } catch {
        // The parsers are not made for every input, for example extreme nesting. A destination that is not found only leaves its link as it was.
        return [];
    }
}

function locateDestinations(source: string): Destination[] {
    const run = new Run(source);
    createParser(run).parse(run.normalized, {});
    const markdown = run.destinations.map(([start, end]): OffsetRange => [run.toSource(start), run.toSource(end)]);
    const view = htmlView(run);
    const kept = mergeRanges(run.kept.map(([start, end]): OffsetRange => [run.toSource(start), run.toSource(end)]));
    const excluded = excludedMarkdownDestinations(view, source, markdown);
    const destinations: Destination[] = markdown
        .filter((_, index) => !excluded.has(index))
        .map(([start, end]) => ({ start, end, url: source.slice(start, end) }))
        // A quoted value that continues on a block quote or list line has the markers or indentation of that line in its range. The range is
        // then not the value, and replacing it would damage them, so the link is left as it is. Only a value in one stretch of the text that
        // the parser read as prose or HTML, and that Markdown syntax does not touch, counts.
        .concat(htmlDestinations(view).filter((destination) => isPlainText(destination, view, source, kept)));

    // Whatever the parsers report, only non-empty ranges inside the text that do not overlap are handed on, as the importer replaces them one by one.
    const result: Destination[] = [];
    let previousEnd = 0;
    for (const destination of destinations.sort((a, b) => a.start - b.start || a.end - b.end)) {
        if (destination.start >= previousEnd && destination.end > destination.start && destination.end <= source.length) {
            result.push(destination);
            previousEnd = destination.end;
        }
    }
    return result;
}

/** Sorts ranges and joins those that touch or overlap. */
function mergeRanges(ranges: OffsetRange[]): OffsetRange[] {
    const merged: OffsetRange[] = [];
    for (const [start, end] of [...ranges].sort((a, b) => a[0] - b[0])) {
        const last = merged[merged.length - 1];
        if (last && start <= last[1]) {
            last[1] = Math.max(last[1], end);
        } else {
            merged.push([start, end]);
        }
    }
    return merged;
}

function isPlainText({ start, end }: Destination, view: string, source: string, kept: OffsetRange[]): boolean {
    return view.slice(start, end) === source.slice(start, end) && kept.some(([keptStart, keptEnd]) => keptStart <= start && end <= keptEnd);
}

// =====================
// Source text and offsets
// =====================

/** What one parse collects. The parser sees the text with `\n` line ends; its offsets are converted back to the source's with `toSource`. */
class Run {
    readonly normalized: string;
    /** Destinations of Markdown links, images, definitions and autolinks. */
    readonly destinations: OffsetRange[] = [];
    /** Text that is prose or raw HTML and so part of what the HTML parser reads. */
    readonly kept: OffsetRange[] = [];
    /** Text inside the kept ranges that is Markdown syntax: code, escapes, link markers, autolinks. */
    readonly masked: OffsetRange[] = [];
    private readonly normalizedLineStarts: number[] = [0];
    private readonly sourceLineStarts: number[] = [0];

    constructor(readonly source: string) {
        this.normalized = source.replace(/\r\n?/g, '\n').replace(/\0/g, '�');
        const lineEnds = /\r\n|\r|\n/g;
        let match: RegExpExecArray | null;
        while ((match = lineEnds.exec(source)) !== null) {
            const sourceStart = match.index + match[0].length;
            const lineLength = match.index - this.sourceLineStarts[this.sourceLineStarts.length - 1];
            this.normalizedLineStarts.push(this.normalizedLineStarts[this.normalizedLineStarts.length - 1] + lineLength + 1);
            this.sourceLineStarts.push(sourceStart);
        }
    }

    toSource(offset: number): number {
        let low = 0;
        let high = this.normalizedLineStarts.length - 1;
        while (low < high) {
            const middle = (low + high + 1) >> 1;
            if (this.normalizedLineStarts[middle] <= offset) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return this.sourceLineStarts[low] + offset - this.normalizedLineStarts[low];
    }
}

/** Gives the document ranges of the text between two offsets in a text that the parser sees, such as the content of a paragraph. */
type Frame = (start: number, end: number) => OffsetRange[];

/** A line of a parsed text and the position in the document where it starts. */
interface Segment {
    textStart: number;
    textEnd: number;
    documentStart: number;
}

/**
 * The parser removes indentation and block quote and list markers from the text of a paragraph, an HTML block or a heading, so that text
 * is made of one segment per line.
 */
function segmentFrame(segments: Segment[], leadingTrimmed = 0): Frame {
    return (start, end) => {
        const textStart = start + leadingTrimmed;
        const textEnd = end + leadingTrimmed;
        // The segments are in order and do not overlap, so the ones that the span touches are a run that begins at the first one ending after it starts.
        let low = 0;
        let high = segments.length;
        while (low < high) {
            const middle = (low + high) >> 1;
            if (segments[middle].textEnd > textStart) {
                high = middle;
            } else {
                low = middle + 1;
            }
        }
        const ranges: OffsetRange[] = [];
        for (let index = low; index < segments.length && segments[index].textStart < textEnd; index++) {
            const segment = segments[index];
            const from = Math.max(textStart, segment.textStart);
            const to = Math.min(textEnd, segment.textEnd);
            if (from < to) {
                ranges.push([segment.documentStart + from - segment.textStart, segment.documentStart + to - segment.textStart]);
            }
        }
        return ranges;
    };
}

function isSpace(code: number): boolean {
    return code === 0x20 || code === 0x09;
}

function isAsciiTrimmable(code: number): boolean {
    return isSpace(code) || code === 0x0a || code === 0x0d;
}

/** Reproduces how the parser builds the text of lines of a block (`StateBlock.getLines`), but returns where each line lies in the document. */
function mapLines(state: StateBlock, begin: number, end: number, keepLastLineFeed: boolean): { segments: Segment[]; leadingTrimmed: number } {
    const segments: Segment[] = [];
    let textLength = 0;
    let leadingTrimmed = -1;
    for (let line = begin; line < end; line++) {
        let lineIndent = 0;
        const lineStart = state.bMarks[line];
        let first = lineStart;
        const last = line + 1 < end || keepLastLineFeed ? state.eMarks[line] + 1 : state.eMarks[line];
        while (first < last && lineIndent < state.blkIndent) {
            const code = state.src.charCodeAt(first);
            if (isSpace(code)) {
                lineIndent += code === 0x09 ? 4 - ((lineIndent + state.bsCount[line]) % 4) : 1;
            } else if (first - lineStart < state.tShift[line]) {
                lineIndent++;
            } else {
                break;
            }
            first++;
        }
        // A tab that is only partly part of the indentation leaves spaces in front of the text of the line.
        const padding = Math.max(lineIndent - state.blkIndent, 0);
        segments.push({ textStart: textLength + padding, textEnd: textLength + padding + (last - first), documentStart: first });
        if (leadingTrimmed < 0) {
            // The text of a paragraph is trimmed, so offsets in it start after the first line's leading white space.
            let trimmed = 0;
            while (isAsciiTrimmable(state.src.charCodeAt(first + trimmed)) && first + trimmed < last) {
                trimmed++;
            }
            leadingTrimmed = padding + trimmed;
        }
        textLength += padding + last - first;
    }
    return { segments, leadingTrimmed: Math.max(leadingTrimmed, 0) };
}

// =====================
// The Markdown parser
// =====================

/**
 * A CommonMark parser that only reports where things are; its result is never rendered. It is the one of the Markdown preview with raw HTML
 * enabled and every link accepted, as Markdown itself does. markdown-it reports source lines for blocks but no columns for inline tokens, so
 * the rules that matter are wrapped to record, in document offsets, the destinations they consume, the text that is prose or raw HTML and
 * the text that is Markdown syntax.
 */
function createParser(run: Run): Parser {
    const parser = MarkdownIt('commonmark', { html: true, maxNesting: 100 });
    parser.validateLink = () => true;
    parser.core.ruler.disable('normalize');

    const frames = new WeakMap<Token, Frame>();
    /** The frames inline rules use: the one of the inline token being parsed, with the one of an image's label on top while that is parsed. */
    let frameStack: Frame[] = [];
    /** Where the line after the last definition is parsed: the line, and the block context (nesting and indentation) that accepted the definition. */
    let afterDefinition = { line: -1, level: -1, blkIndent: -1 };
    let definitionDestinations: OffsetRange[] | undefined;

    const topFrame = (): Frame | undefined => frameStack[frameStack.length - 1];
    const maskInline = (start: number, end: number) => run.masked.push(...(topFrame()?.(start, end) ?? []));
    const recordDestination = (start: number, end: number) => {
        const ranges = end > start ? (topFrame()?.(start, end) ?? []) : [];
        if (ranges.length === 1) {
            run.destinations.push(ranges[0]);
        }
    };

    parser.core.ruler.at('inline', (state) => {
        for (const token of state.tokens) {
            if (token.type === 'inline') {
                const frame = frames.get(token);
                frameStack = frame ? [frame] : [];
                state.md.inline.parse(token.content, state.md, state.env, (token.children = []));
            }
        }
    });

    // The destination parser of markdown-it lets a backslash escape a line ending. In CommonMark a destination never contains a line ending, and
    // the backslash is an ordinary character there. The wrapper also notes where a definition's destination is, which its rule does not report.
    const helpers: { parseLinkDestination: Parser['helpers']['parseLinkDestination'] } = parser.helpers;
    const parseLinkDestination = helpers.parseLinkDestination;
    helpers.parseLinkDestination = (text, start, max) => {
        let result = parseLinkDestination(text, start, max);
        const angle = text.charCodeAt(start) === 0x3c;
        if (result.ok) {
            const lineEnd = text.slice(start, result.pos).indexOf('\n');
            if (lineEnd >= 0) {
                result = angle ? { ok: false, pos: 0, str: '' } : parseLinkDestination(text, start, start + lineEnd);
            }
        }
        if (result.ok) {
            definitionDestinations?.push([start + (angle ? 1 : 0), result.pos - (angle ? 1 : 0)]);
        }
        return result;
    };

    // In CommonMark a reference definition is the start of a paragraph, so the line after it continues that paragraph unless it could
    // interrupt one (an indented line is not a code block, a list item must start with 1). markdown-it ends the block after a definition
    // instead, so the rules that cannot interrupt a paragraph are held back on that line.
    const wrapBlockRule = (name: string, record?: (state: StateBlock, startLine: number) => void) => {
        const rule = parser.block.ruler.__rules__.find((candidate) => candidate.name === name);
        if (!rule) {
            throw new Error(`Markdown rule ${name} not found`);
        }
        const { fn, alt } = rule;
        const holdBack = !['reference', 'lheading', 'paragraph'].includes(name);
        parser.block.ruler.at(
            name,
            (state, startLine, endLine, silent) => {
                if (
                    holdBack &&
                    !silent &&
                    afterDefinition.line === startLine &&
                    afterDefinition.level === state.level &&
                    afterDefinition.blkIndent === state.blkIndent &&
                    !(alt.includes('paragraph') && fn(state, startLine, endLine, true))
                ) {
                    return false;
                }
                const matched = fn(state, startLine, endLine, silent);
                if (matched && !silent) {
                    record?.(state, startLine);
                }
                return matched;
            },
            { alt },
        );
    };
    for (const name of ['code', 'fence', 'blockquote', 'hr', 'list']) {
        wrapBlockRule(name);
    }

    // The text of paragraphs and headings is prose and raw HTML; the inline rules below take Markdown syntax out of it.
    const keepInline = (state: StateBlock, frame: Frame, length: number) => {
        const token = state.tokens[state.tokens.length - 2];
        if (token?.type === 'inline') {
            frames.set(token, frame);
            run.kept.push(...frame(0, length));
        }
    };
    const recordParagraph = (state: StateBlock) => {
        const token = state.tokens[state.tokens.length - 2];
        if (token?.map) {
            const { segments, leadingTrimmed } = mapLines(state, token.map[0], token.map[1], false);
            keepInline(state, segmentFrame(segments, leadingTrimmed), token.content.length);
        }
    };
    wrapBlockRule('paragraph', recordParagraph);
    wrapBlockRule('lheading', recordParagraph);
    wrapBlockRule('heading', (state, startLine) => {
        // The text of an ATX heading is the rest of its line after the `#` characters, without surrounding spaces and a closing sequence.
        let position = state.bMarks[startLine] + state.tShift[startLine];
        while (state.src.charCodeAt(position) === 0x23) {
            position++;
        }
        while (position < state.eMarks[startLine] && isAsciiTrimmable(state.src.charCodeAt(position))) {
            position++;
        }
        const token = state.tokens[state.tokens.length - 2];
        keepInline(state, segmentFrame([{ textStart: 0, textEnd: token.content.length, documentStart: position }]), token.content.length);
    });
    wrapBlockRule('html_block', (state) => {
        const token = state.tokens[state.tokens.length - 1];
        if (token?.type === 'html_block' && token.map) {
            run.kept.push(...segmentFrame(mapLines(state, token.map[0], token.map[1], true).segments)(0, token.content.length));
        }
    });

    // markdown-it does not report where a definition's destination is, so the calls of the destination parser during its rule are noted.
    const definition = parser.block.ruler.__rules__.find((candidate) => candidate.name === 'reference');
    if (!definition) {
        throw new Error('Markdown rule reference not found');
    }
    const { fn: parseDefinition, alt: definitionAlt } = definition;
    parser.block.ruler.at(
        'reference',
        (state, startLine, endLine, silent) => {
            definitionDestinations = [];
            const matched = parseDefinition(state, startLine, endLine, silent);
            // The last destination that the rule parsed is the one of the definition it just accepted.
            const destination = definitionDestinations[definitionDestinations.length - 1];
            definitionDestinations = undefined;
            if (matched && !silent) {
                afterDefinition = { line: state.line, level: state.level, blkIndent: state.blkIndent };
                if (destination && destination[1] > destination[0]) {
                    run.destinations.push(destination);
                }
            }
            return matched;
        },
        { alt: definitionAlt },
    );

    const wrapInlineRule = (name: string, record: (state: StateInline, start: number, tokenCount: number) => void) => {
        const rule = parser.inline.ruler.__rules__.find((candidate) => candidate.name === name)?.fn;
        if (!rule) {
            throw new Error(`Markdown rule ${name} not found`);
        }
        parser.inline.ruler.at(name, (state, silent) => {
            const start = state.pos;
            const tokenCount = state.tokens.length;
            const matched = rule(state, silent);
            if (matched && !silent) {
                record(state, start, tokenCount);
            }
            return matched;
        });
    };

    // A link or image: the syntax around the label is Markdown, the label itself is prose, which the rules that run inside it handle. A
    // destination is only present in the inline form `[label](destination "title")`, which is the one that ends with a parenthesis. The alt
    // text of an image is parsed again on its own, so while that happens offsets in it are relative to the label.
    for (const name of ['link', 'image']) {
        const rule = parser.inline.ruler.__rules__.find((candidate) => candidate.name === name)?.fn;
        if (!rule) {
            throw new Error(`Markdown rule ${name} not found`);
        }
        const isImage = name === 'image';
        parser.inline.ruler.at(name, (state, silent) => {
            const start = state.pos;
            const outer = topFrame();
            const labelStart = start + (isImage ? 2 : 1);
            const labelFrame: Frame | undefined = outer && isImage && !silent ? (from, to) => outer(labelStart + from, labelStart + to) : undefined;
            if (labelFrame) {
                frameStack.push(labelFrame);
            }
            let matched: boolean;
            try {
                matched = rule(state, silent);
            } finally {
                if (labelFrame) {
                    frameStack.pop();
                }
            }
            if (!matched || silent) {
                return matched;
            }
            const labelEnd = state.md.helpers.parseLinkLabel(state, start + (isImage ? 1 : 0), !isImage);
            maskInline(start, labelStart);
            maskInline(labelEnd, state.pos);
            if (state.src.charCodeAt(state.pos - 1) === 0x29 && state.src.charCodeAt(labelEnd + 1) === 0x28) {
                let position = labelEnd + 2;
                while (position < state.posMax && (isSpace(state.src.charCodeAt(position)) || state.src.charCodeAt(position) === 0x0a)) {
                    position++;
                }
                const destination = state.md.helpers.parseLinkDestination(state.src, position, state.posMax);
                if (destination.ok) {
                    const angle = state.src.charCodeAt(position) === 0x3c ? 1 : 0;
                    recordDestination(position + angle, destination.pos - angle);
                }
            }
            return matched;
        });
    }
    wrapInlineRule('autolink', (state, start) => {
        maskInline(start, state.pos);
        const text = state.src.slice(start + 1, state.pos - 1);
        // An absolute URI, as opposed to an email address, has a destination.
        if (isAbsoluteUri(text)) {
            recordDestination(start + 1, state.pos - 1);
        }
    });
    // The rule for backtick sequences also matches when there is no closing sequence; only a code span is code.
    wrapInlineRule('backticks', (state, start, tokenCount) => {
        if (state.tokens.length > tokenCount && state.tokens[state.tokens.length - 1].type === 'code_inline') {
            maskInline(start, state.pos);
        }
    });
    // A backslash only escapes the next character if that is ASCII punctuation, and before a line end it is a hard break; otherwise it is text.
    wrapInlineRule('escape', (state, start) => {
        const next = state.src[start + 1] ?? '';
        if (next === '\n') {
            maskInline(start, start + 1);
        } else if (/^[!-/:-@[-`{-~]$/.test(next)) {
            maskInline(start, start + 2);
        }
    });
    wrapInlineRule('entity', (state, start) => maskInline(start, state.pos));
    return parser;
}

/** Whether the text between the angle brackets of an autolink is a scheme, a colon and characters that are neither white space, control nor angle brackets. */
function isAbsoluteUri(text: string): boolean {
    const scheme = /^[a-zA-Z][a-zA-Z0-9+.-]{1,31}:/.exec(text);
    for (let index = scheme?.[0].length ?? text.length; index < text.length; index++) {
        const code = text.charCodeAt(index);
        if (code <= 0x20 || code === 0x3c || code === 0x3e) {
            return false;
        }
    }
    return scheme !== null;
}

// =====================
// Embedded HTML
// =====================

const EXCLUDED_ELEMENT_SELECTOR = 'pre, code, script, style, textarea';
const RAW_TEXT_ELEMENT_SELECTOR = 'script, style, textarea';
const HTML_WHITESPACE = '\\t\\n\\f\\r ';
// The HTML parser turns an `image` tag (outside SVG) into an `img` element, so it counts as one.
const DESTINATION_TAG = new RegExp(`<(a|img|image)(?=[${HTML_WHITESPACE}/>])`, 'gi');
const EXCLUDED_TAG = new RegExp(`<(pre|code|script|style|textarea)(?=[${HTML_WHITESPACE}/>])`, 'i');

/**
 * Builds the text that the HTML parser reads: the prose and raw HTML of the document at their original positions and spaces everywhere else,
 * so that code examples, escaped tags and link syntax never become elements. Line breaks stay, so that the structure of lines does too.
 */
function htmlView(run: Run): string {
    const { source } = run;
    const view = source.replace(/[^\r\n]/g, ' ').split('');
    for (const [start, end] of run.kept) {
        for (let index = run.toSource(start); index < Math.min(run.toSource(end), source.length); index++) {
            view[index] = source[index];
        }
    }
    for (const [start, end] of run.masked) {
        for (let index = run.toSource(start); index < Math.min(run.toSource(end), source.length); index++) {
            view[index] = source[index] === '\r' || source[index] === '\n' ? source[index] : ' ';
        }
    }
    return view.join('');
}

/** Parses markup the way the browser parses a fragment. The content of a template is inert: nothing in it is run or loaded. */
function parseHtml(markup: string): DocumentFragment {
    const template = document.createElement('template');
    template.innerHTML = markup;
    return template.content;
}

/** A private-use character that the text does not contain, or undefined if it contains all of them from `from` on. */
function unusedCharacter(source: string, from: number): string | undefined {
    for (let code = from; code <= 0xf8ff; code++) {
        if (!source.includes(String.fromCharCode(code))) {
            return String.fromCharCode(code);
        }
    }
    return undefined;
}

/**
 * Finds which of the Markdown destinations lie inside an element whose content is not searched, as a set of indexes into `destinations`.
 * A `script`, `style` or `textarea` that is not closed does not count: its text runs to the end, but the links in it are still found. (The
 * HTML parser used before reported such an element as ending where it starts.) A destination that ends up inside a tag, such as in
 * `<code[x](y)>`, cannot be placed in the tree and is not excluded either.
 */
function excludedMarkdownDestinations(view: string, source: string, destinations: OffsetRange[]): Set<number> {
    const excluded = new Set<number>();
    if (destinations.length === 0 || !EXCLUDED_TAG.test(view)) {
        return excluded;
    }
    // Each destination is marked by a private-use character pair around its index, which the parser keeps as text wherever it ends up; the
    // end of the text is marked by a comment, whose parents are the elements that are still open there.
    const open = unusedCharacter(source, 0xe000);
    const close = open && unusedCharacter(source, open.charCodeAt(0) + 1);
    if (!open || !close) {
        // A text with all the private-use characters (which only hostile input has) leaves no way to mark a destination, so none is excluded.
        return excluded;
    }
    let markup = view;
    destinations
        .map(([start], index) => ({ start, text: `${open}${index}${close}` }))
        .sort((a, b) => b.start - a.start)
        .forEach(({ start, text }) => (markup = markup.slice(0, start) + text + markup.slice(start)));
    const end = `${open}end${close}`;
    const walker = document.createTreeWalker(parseHtml(`${markup}<!--${end}-->`), NodeFilter.SHOW_TEXT | NodeFilter.SHOW_COMMENT);
    const marker = new RegExp(`${open}(\\d+)${close}`, 'g');
    const openAtEnd = new Set<Element>();
    const texts: Text[] = [];
    for (let node = walker.nextNode(); node; node = walker.nextNode()) {
        if (node.nodeValue?.includes(end)) {
            for (let element = node.parentElement; element; element = element.parentElement) {
                openAtEnd.add(element);
            }
        }
        if (node.nodeType === Node.TEXT_NODE) {
            texts.push(node as Text);
        }
    }
    for (const text of texts) {
        for (let element = text.parentElement; element; element = element.parentElement) {
            if (element.matches(EXCLUDED_ELEMENT_SELECTOR) && !(openAtEnd.has(element) && element.matches(RAW_TEXT_ELEMENT_SELECTOR))) {
                for (const match of (text.nodeValue ?? '').matchAll(marker)) {
                    excluded.add(Number(match[1]));
                }
                break;
            }
        }
    }
    return excluded;
}

/** Finds the destinations of `<a href>` and `<img src>` that are not inside an element whose content is not searched. */
function htmlDestinations(view: string): Destination[] {
    if (!new RegExp(DESTINATION_TAG.source, 'i').test(view)) {
        return [];
    }
    // Every start tag of an anchor or image gets its offset as an attribute, so each element that the parser creates can be traced to its source.
    // The value has no quotes and is followed by a space, so it cannot change how the rest of the tag is read.
    const markup = view.replace(DESTINATION_TAG, (tag, name: string, offset: number) => `<${name} data-offset=${offset} `);
    const destinations: Destination[] = [];
    const seen = new Set<number>();
    for (const element of parseHtml(markup).querySelectorAll('[data-offset]')) {
        const offset = Number(element.getAttribute('data-offset'));
        const attributeName = element.localName === 'img' ? 'src' : element.localName === 'a' ? 'href' : undefined;
        // When elements are misnested, the HTML parser re-creates open elements such as `a` with the same attributes after the element that
        // ended them. Only the first element made from a tag is that tag; later ones are copies, wherever they end up.
        if (!attributeName || seen.has(offset)) {
            continue;
        }
        seen.add(offset);
        if (!element.closest(EXCLUDED_ELEMENT_SELECTOR)) {
            const tagNameLength = /^[a-z]+/i.exec(view.slice(offset + 1))?.[0].length ?? 0;
            const value = attributeValue(view, offset + 1 + tagNameLength, attributeName);
            if (value?.url) {
                destinations.push({ start: value.start, end: value.end, url: value.url });
            }
        }
    }
    return destinations;
}

/**
 * Finds the value of the first attribute with the given name in the start tag whose attributes begin at `position`, following the HTML
 * tokenizer: the value is quoted with `"` or `'`, or it ends at white space or `>`. The quotes are not part of the range.
 * @return the range of the value and the value itself, with character references decoded; undefined if the attribute has no value
 */
function attributeValue(text: string, position: number, name: string): Destination | undefined {
    const isWhitespace = (index: number) => '\t\n\f\r '.includes(text[index]);
    let index = position;
    while (index < text.length) {
        while (index < text.length && (isWhitespace(index) || text[index] === '/')) {
            index++;
        }
        if (index >= text.length || text[index] === '>') {
            return undefined;
        }
        // A leading `=` belongs to the name of the attribute.
        const nameStart = index++;
        while (index < text.length && !isWhitespace(index) && text[index] !== '/' && text[index] !== '>' && text[index] !== '=') {
            index++;
        }
        const attributeName = text.slice(nameStart, index).toLowerCase();
        while (index < text.length && isWhitespace(index)) {
            index++;
        }
        let value: { start: number; end: number; quote: string } | undefined;
        if (text[index] === '=') {
            index++;
            while (index < text.length && isWhitespace(index)) {
                index++;
            }
            const quote = text[index] === '"' || text[index] === "'" ? text[index] : '';
            const start = index + quote.length;
            const end = quote ? text.indexOf(quote, start) : text.slice(start).search(/[\t\n\f\r >]|$/) + start;
            if (end < 0) {
                return undefined;
            }
            value = { start, end, quote };
            index = end + quote.length;
        }
        if (attributeName === name) {
            return value && { start: value.start, end: value.end, url: decodeAttributeValue(text.slice(value.start, value.end), value.quote) };
        }
    }
    return undefined;
}

/** Decodes the character references in an attribute value the way the HTML parser does. */
function decodeAttributeValue(raw: string, quote: string): string {
    // The HTML parser reads line ends as `\n` and a null character as the replacement character.
    const text = raw.replace(/\r\n?/g, '\n').replace(/\0/g, '\uFFFD');
    return text.includes('&') ? (parseHtml(`<a href=${quote}${text}${quote}>`).firstElementChild?.getAttribute('href') ?? text) : text;
}
