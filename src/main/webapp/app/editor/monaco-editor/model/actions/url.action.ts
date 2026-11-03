import { faLink } from '@fortawesome/free-solid-svg-icons';
import { TextStyleTextEditorAction } from 'app/editor/monaco-editor/model/actions/text-editor-action.model';
import { TextEditor } from 'app/editor/monaco-editor/model/actions/adapter/text-editor.interface';
import { isHttpUrl, sanitizeStringForMarkdownEditor } from 'app/foundation/util/markdown.util';
import { TextEditorRange } from 'app/editor/monaco-editor/model/actions/adapter/text-editor-range.model';
import { Disposable } from 'app/editor/monaco-editor/model/actions/monaco-editor.util';
import { TranslateService } from '@ngx-translate/core';
import MarkdownIt from 'markdown-it';

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

/**
 * Records a range in the parser environment, which collects them for the paste context check of {@link UrlAction}.
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
 * values may contain `>`. Comments, declarations and processing instructions are not recognized, they are rare in exercise texts.
 * @param source The text to read from.
 * @param start The position of the `<`.
 * @return the end of the tag (exclusive) and whether it opens or closes an anchor, or undefined if no tag starts here
 */
function readHtmlTag(source: string, start: number): { end: number; anchor?: 'open' | 'close' } | undefined {
    if (source[start] !== '<') {
        return undefined;
    }
    let position = start + 1;
    const closing = source[position] === '/';
    if (closing) {
        position++;
    }
    const nameStart = position;
    if (!/[A-Za-z]/.test(source[position] ?? '')) {
        return undefined;
    }
    while (position < source.length && /[A-Za-z0-9-]/.test(source[position])) {
        position++;
    }
    const isAnchor = source.slice(nameStart, position).toLowerCase() === 'a';
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
            return { end: position + 1, anchor: isAnchor ? (closing ? 'close' : 'open') : undefined };
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
        recordRange(state.env, { source: state.src, start: state.pos, end: tag.end, anchor: tag.anchor });
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

interface UrlArguments {
    text: string;
    url: string;
}

/**
 * Action to insert a URL into the editor. They follow the format [text](url).
 */
export class UrlAction extends TextStyleTextEditorAction {
    static readonly ID = 'url.action';
    static readonly DEFAULT_LINK_TEXT = 'Link';
    static readonly DEFAULT_LINK_PLACEHOLDER = 'https://';
    static readonly DEFAULT_INSERT_TEXT = `[🔗 ${this.DEFAULT_LINK_TEXT}](${this.DEFAULT_LINK_PLACEHOLDER})`;

    private disposablePasteInterceptor?: Disposable;

    constructor(private canExecute: () => boolean = () => true) {
        super(UrlAction.ID, 'artemisApp.multipleChoiceQuestion.editor.link', faLink, undefined);
    }

    setExecutionGuard(canExecute: () => boolean): void {
        this.canExecute = canExecute;
    }

    /**
     * Registers the action with the given editor and adds a paste interceptor that turns the selected text into a link when a URL is pasted over it.
     * @param editor The editor to register the action in.
     * @param translateService The translation service to use for translating the action label.
     */
    override register(editor: TextEditor, translateService: TranslateService) {
        super.register(editor, translateService);
        this.disposablePasteInterceptor = editor.addPasteInterceptor((pastedText) => this.wrapSelectionWithPastedUrl(editor, pastedText));
    }

    /**
     * Disposes the action and removes the paste interceptor from the editor it was registered in.
     */
    override dispose() {
        super.dispose();
        this.disposablePasteInterceptor?.dispose();
        this.disposablePasteInterceptor = undefined;
    }

    /**
     * Executes the action in the current editor with the given arguments (url and text).
     * @param args The text and url of the URL to insert. If one or both are not provided, checks for selected text to wrap.
     */
    override executeInCurrentEditor(args?: UrlArguments): void {
        if (this.canExecute()) {
            super.executeInCurrentEditor(args);
        }
    }

    /**
     * Inserts, at the current selection, the markdown URL with the given text and url if they were provided.
     * If no arguments are provided and there is selected text, wraps the selected text with [selectedText](https://).
     * Otherwise, inserts the default text.
     * @param editor The editor in which to insert the URL.
     * @param args The text and url of the URL to insert. If one or both are not provided, checks for selected text to wrap.
     */
    run(editor: TextEditor, args?: UrlArguments): void {
        if (!args?.text || !args?.url) {
            this.wrapSelectionOrInsertDefault(
                editor,
                (selectedText) => `[${sanitizeStringForMarkdownEditor(selectedText)}](${UrlAction.DEFAULT_LINK_PLACEHOLDER})`,
                UrlAction.DEFAULT_INSERT_TEXT,
            );
            this.shrinkSelectionToUrlPart(editor);
        } else {
            this.replaceTextAtCurrentSelection(editor, `[${sanitizeStringForMarkdownEditor(args.text)}](${args.url})`);
        }
        editor.focus();
    }

    /**
     * If the pasted text is a single http(s) URL and text on one line is selected, replaces the selection with "[selectedText](url)"
     * and places the cursor after the link. The selected text is kept as it is, only its backslashes and square brackets are escaped.
     * The paste is left to the editor if the action may not run, or if the selection is blank, is itself a URL, or lies inside an
     * existing link or in code.
     * @param editor The editor in which the text was pasted.
     * @param pastedText The text the user pasted.
     * @return true if the selection was replaced by a link, false if the editor should paste the text as usual.
     */
    private wrapSelectionWithPastedUrl(editor: TextEditor, pastedText: string): boolean {
        const selection = editor.getSelection();
        const start = selection.getStartPosition();
        const end = selection.getEndPosition();
        if (!this.canExecute() || !isHttpUrl(pastedText) || start.getLineNumber() !== end.getLineNumber() || start.getColumn() === end.getColumn()) {
            return false;
        }
        const selectedText = this.getTextAtRange(editor, selection) ?? '';
        if (!selectedText.trim() || isHttpUrl(selectedText) || this.isInLinkOrCode(editor, selection)) {
            return false;
        }
        const label = selectedText.replace(/[\\[\]]/g, '\\$&');
        // Keep the URL as copied; only encode the parentheses that would end the markdown link early.
        const url = pastedText.trim().replaceAll('(', '%28').replaceAll(')', '%29');
        const link = `[${label}](${url})`;
        this.replaceTextAtRange(editor, selection, link);
        editor.setPosition(start.withColumn(start.getColumn() + link.length));
        return true;
    }

    /**
     * Checks whether a selection on one line lies where a pasted link would not work: inside or across an existing link (its label, or its
     * destination such as the placeholder the link button selects), an image, an autolink, an HTML tag or anchor, inline code, inside a fenced or
     * indented code block, or anywhere outside paragraph, heading and table text, such as a reference definition or a list marker.
     * The unchanged document is parsed with the same CommonMark rules as the preview, so references and multiline code spans resolve as there.
     * @param editor The editor that contains the selection.
     * @param selection The selection, which starts and ends on the same line.
     */
    private isInLinkOrCode(editor: TextEditor, selection: TextEditorRange): boolean {
        const lineIndex = selection.getStartPosition().getLineNumber() - 1;
        const startIndex = selection.getStartPosition().getColumn() - 1;
        const endIndex = selection.getEndPosition().getColumn() - 1;
        const lines = Array.from({ length: this.getLineCount(editor) }, (_, index) => this.getLineText(editor, index + 1) ?? '');
        const inlineRanges: InlineRange[] = [];
        const tokens = markdownParser.parse(lines.join('\n'), { inlineRanges });
        const coversLine = (map: [number, number] | null) => !!map && map[0] <= lineIndex && lineIndex < map[1];

        if (tokens.some((token) => (token.type === 'fence' || token.type === 'code_block') && coversLine(token.map))) {
            return true;
        }
        // Table cells carry no lines of their own, so an inline token without lines takes those of the block token before it, its row. The
        // cells of a row are searched one after the other, so each one is found in its own columns rather than in an earlier cell.
        let blockMap: [number, number] | null = null;
        let cellSearchStart = 0;
        for (const token of tokens) {
            if (token.map) {
                cellSearchStart = 0;
            }
            const map: [number, number] | null = token.map ?? blockMap;
            blockMap = map;
            if (token.type !== 'inline' || !map || !coversLine(map)) {
                continue;
            }
            const contentLines = token.content.split('\n');
            const contentLineIndex = lineIndex - map[0];
            const contentLine = contentLines[contentLineIndex];
            if (contentLine === undefined) {
                // the underline of a heading, which belongs to the heading's lines but not to its text
                continue;
            }
            // The text of a paragraph or heading ends its source line; a cell is the next occurrence after the previous cell.
            const contentStart = token.map ? lines[lineIndex].lastIndexOf(contentLine) : lines[lineIndex].indexOf(contentLine, cellSearchStart);
            if (!token.map && contentStart !== -1) {
                cellSearchStart = contentStart + contentLine.length;
            }
            if (contentStart === -1 || startIndex < contentStart || contentStart + contentLine.length < endIndex) {
                continue;
            }
            if (readHtmlTag(token.content, token.content.length - token.content.trimStart().length)) {
                // a paragraph that starts with an HTML tag is a raw HTML block wherever HTML is rendered, and no Markdown is parsed inside it
                return true;
            }
            const lineOffset = contentLines.slice(0, contentLineIndex).reduce((offset, line) => offset + line.length + 1, 0);
            const start = lineOffset + startIndex - contentStart;
            const end = lineOffset + endIndex - contentStart;
            const ranges = withAnchorLabels(
                inlineRanges.filter((range) => range.source === token.content),
                token.content.length,
            );
            return ranges.some((range) => range.start < end && start < range.end);
        }
        // The selection is not inside the text of a paragraph, heading or table cell, so the paste is left to the editor.
        return true;
    }

    /**
     * If there is currently selected text, it will be "[something](https://)".
     * This shrinks the selection range to only include "https://",
     * allowing users to immediately replace the placeholder text.
     * @param editor The editor in which to update the text selection.
     */
    private shrinkSelectionToUrlPart(editor: TextEditor): void {
        const selection = editor.getSelection();
        if (selection) {
            const end = selection.getEndPosition();
            // Exclude everything up to "("
            const newStart = end.withColumn(end.getColumn() - UrlAction.DEFAULT_LINK_PLACEHOLDER.length - 1);
            // Exclude trailing ")"
            const newEnd = end.withColumn(end.getColumn() - 1);
            editor.setSelection(new TextEditorRange(newStart, newEnd));
        }
    }
}
