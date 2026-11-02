import { faLink } from '@fortawesome/free-solid-svg-icons';
import { TextStyleTextEditorAction } from 'app/editor/monaco-editor/model/actions/text-editor-action.model';
import { TextEditor } from 'app/editor/monaco-editor/model/actions/adapter/text-editor.interface';
import { isHttpUrl, sanitizeStringForMarkdownEditor } from 'app/foundation/util/markdown.util';
import { TextEditorRange } from 'app/editor/monaco-editor/model/actions/adapter/text-editor-range.model';
import { Disposable } from 'app/editor/monaco-editor/model/actions/monaco-editor.util';
import { TranslateService } from '@ngx-translate/core';

interface CodeFence {
    marker: string;
    length: number;
    info: string;
}

/**
 * Reads a Markdown code fence line: at most three spaces, then at least three backticks or tildes, then an optional info string,
 * which may not contain a backtick after a backtick fence.
 * @param line The line to read.
 * @return the fence's character, length and the text after it, or undefined if the line is no code fence.
 */
function readCodeFence(line: string): CodeFence | undefined {
    let indent = 0;
    while (line[indent] === ' ') {
        indent++;
    }
    const marker = line[indent];
    if (indent > 3 || (marker !== '`' && marker !== '~')) {
        return undefined;
    }
    let end = indent;
    while (line[end] === marker) {
        end++;
    }
    const info = line.slice(end);
    if (end - indent < 3 || (marker === '`' && info.includes('`'))) {
        return undefined;
    }
    return { marker, length: end - indent, info };
}

/**
 * Checks whether a line is indented by at least four columns before its first character, which makes it indented code unless it
 * continues a paragraph. A tab advances to the next multiple of four columns.
 * @param line The line to check.
 * @return true if the line has text indented by four columns or more, false for a blank or less indented line.
 */
function isIndentedByFourColumns(line: string): boolean {
    let column = 0;
    for (const character of line) {
        if (character === ' ') {
            column++;
        } else if (character === '\t') {
            column += 4 - (column % 4);
        } else {
            return column >= 4;
        }
    }
    return false;
}

/**
 * Checks whether a line is an ATX heading: at most three spaces, one to six number signs, then a space, a tab or the line end.
 * @param line The line to check.
 */
function isAtxHeading(line: string): boolean {
    let indent = 0;
    while (line[indent] === ' ') {
        indent++;
    }
    let end = indent;
    while (line[end] === '#') {
        end++;
    }
    const level = end - indent;
    return indent <= 3 && level >= 1 && level <= 6 && (end === line.length || line[end] === ' ' || line[end] === '\t');
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
     * Checks whether a selection on one line lies where a pasted link would not work: inside an existing link, i.e. its label or its
     * destination (such as the placeholder the link button selects), inside an inline code span, or inside a fenced or indented code block.
     * @param editor The editor that contains the selection.
     * @param selection The selection, which starts and ends on the same line.
     */
    private isInLinkOrCode(editor: TextEditor, selection: TextEditorRange): boolean {
        const lineNumber = selection.getStartPosition().getLineNumber();
        const line = this.getLineText(editor, lineNumber) ?? '';
        const startIndex = selection.getStartPosition().getColumn() - 1;
        const endIndex = selection.getEndPosition().getColumn() - 1;
        const overlapsSelection = (pattern: RegExp) => [...line.matchAll(pattern)].some((match) => match.index < endIndex && startIndex < match.index + match[0].length);
        if (overlapsSelection(/\[[^\]]*\]\([^)]*\)/g) || overlapsSelection(/`[^`]*`/g)) {
            return true;
        }
        // A code block is closed only by a fence of the same character, at least as long as the opening one and with no info string.
        // An indented line starts a code block unless it continues a paragraph, so the lines before it decide which one it is.
        let openFence: CodeFence | undefined;
        let inIndentedCode = false;
        let inParagraph = false;
        for (let previousLine = 1; previousLine < lineNumber; previousLine++) {
            const text = this.getLineText(editor, previousLine) ?? '';
            const fence = readCodeFence(text);
            if (openFence) {
                if (fence && fence.marker === openFence.marker && fence.length >= openFence.length && !fence.info.trim()) {
                    openFence = undefined;
                }
            } else if (fence) {
                openFence = fence;
                inIndentedCode = false;
            } else if (text.trim()) {
                inIndentedCode = isIndentedByFourColumns(text) && (inIndentedCode || !inParagraph);
                inParagraph = !inIndentedCode && !isAtxHeading(text);
                continue;
            }
            // Fence lines, fenced code and blank lines end a paragraph; a blank line does not end an indented code block.
            inParagraph = false;
        }
        if (openFence !== undefined || readCodeFence(line) !== undefined) {
            return true;
        }
        return isIndentedByFourColumns(line) && (inIndentedCode || !inParagraph);
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
