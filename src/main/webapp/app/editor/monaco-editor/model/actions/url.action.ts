import { faLink } from '@fortawesome/free-solid-svg-icons';
import { TextStyleTextEditorAction } from 'app/editor/monaco-editor/model/actions/text-editor-action.model';
import { TextEditor } from 'app/editor/monaco-editor/model/actions/adapter/text-editor.interface';
import { isHttpUrl, sanitizeStringForMarkdownEditor } from 'app/foundation/util/markdown.util';
import { TextEditorRange } from 'app/editor/monaco-editor/model/actions/adapter/text-editor-range.model';
import { Disposable } from 'app/editor/monaco-editor/model/actions/monaco-editor.util';
import { TranslateService } from '@ngx-translate/core';
import { isInLinkOrCode } from 'app/editor/monaco-editor/model/actions/markdown-link-context';

/** Punctuation that Markdown would read in a link destination: escapes, table cell borders, entities, code spans and math formulas. */
const DESTINATION_ESCAPED_CHARACTERS = ['\\', '|', '&', '`', '$'];

/**
 * Writes a URL as a Markdown link destination that renders to the same address. The punctuation Markdown would read is escaped with a
 * backslash, which CommonMark allows for any punctuation in a destination, and the parentheses that would end the link early are
 * percent-encoded. Query separators such as `&` are kept.
 * @param url The URL as copied.
 * @return the destination to put between the parentheses of the link
 */
function toLinkDestination(url: string): string {
    let destination = url;
    for (const character of DESTINATION_ESCAPED_CHARACTERS) {
        destination = destination.replaceAll(character, `\\${character}`);
    }
    return destination.replaceAll('(', '%28').replaceAll(')', '%29');
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
        const url = toLinkDestination(pastedText.trim());
        const link = `[${label}](${url})`;
        this.replaceTextAtRange(editor, selection, link);
        editor.setPosition(start.withColumn(start.getColumn() + link.length));
        return true;
    }

    /**
     * Checks whether a selection on one line lies where a pasted link would not work, such as inside an existing link or in code.
     * @param editor The editor that contains the selection.
     * @param selection The selection, which starts and ends on the same line.
     * @return true if the paste must be left to the editor
     */
    private isInLinkOrCode(editor: TextEditor, selection: TextEditorRange): boolean {
        const lines = Array.from({ length: this.getLineCount(editor) }, (_, index) => this.getLineText(editor, index + 1) ?? '');
        const start = selection.getStartPosition();
        return isInLinkOrCode(lines, start.getLineNumber() - 1, start.getColumn() - 1, selection.getEndPosition().getColumn() - 1);
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
