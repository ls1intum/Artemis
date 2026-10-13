import { faLink } from '@fortawesome/free-solid-svg-icons';
import { TextStyleTextEditorAction } from 'app/editor/monaco-editor/model/actions/text-editor-action.model';
import { TextEditor } from 'app/editor/monaco-editor/model/actions/adapter/text-editor.interface';
import { sanitizeStringForMarkdownEditor } from 'app/foundation/util/markdown.util';
import { TextEditorRange } from 'app/editor/monaco-editor/model/actions/adapter/text-editor-range.model';
import { Disposable } from 'app/editor/monaco-editor/model/actions/monaco-editor.util';
import { TranslateService } from '@ngx-translate/core';

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

    constructor() {
        super(UrlAction.ID, 'artemisApp.multipleChoiceQuestion.editor.link', faLink, undefined);
    }

    override register(editor: TextEditor, translateService: TranslateService) {
        super.register(editor, translateService);
        this.disposablePasteInterceptor = editor.addPasteInterceptor((pastedText) => this.wrapSelectionWithPastedUrl(editor, pastedText));
    }

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
        super.executeInCurrentEditor(args);
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
     * and places the cursor after the link. The selection is left alone if it is itself a URL or consists only of characters the
     * sanitizer removes.
     * @param editor The editor in which the text was pasted.
     * @param pastedText The text the user pasted.
     * @return true if the selection was replaced by a link, false if the editor should paste the text as usual.
     */
    private wrapSelectionWithPastedUrl(editor: TextEditor, pastedText: string): boolean {
        const selection = editor.getSelection();
        const start = selection.getStartPosition();
        const end = selection.getEndPosition();
        if (!UrlAction.isHttpUrl(pastedText) || start.getLineNumber() !== end.getLineNumber() || start.getColumn() === end.getColumn()) {
            return false;
        }
        const selectedText = this.getTextAtRange(editor, selection) ?? '';
        const label = sanitizeStringForMarkdownEditor(selectedText) ?? '';
        if (!label.trim() || UrlAction.isHttpUrl(selectedText)) {
            return false;
        }
        // Keep the URL as copied; only encode the parentheses that would end the markdown link early.
        const url = pastedText.trim().replaceAll('(', '%28').replaceAll(')', '%29');
        const link = `[${label}](${url})`;
        this.replaceTextAtRange(editor, selection, link);
        editor.setPosition(start.withColumn(start.getColumn() + link.length));
        return true;
    }

    /**
     * Checks whether the given text, ignoring surrounding whitespace, is exactly one http or https URL.
     * @param text The text to check.
     */
    private static isHttpUrl(text: string): boolean {
        const trimmed = text.trim();
        if (!trimmed || /\s/.test(trimmed)) {
            return false;
        }
        try {
            const protocol = new URL(trimmed).protocol;
            return protocol === 'http:' || protocol === 'https:';
        } catch {
            return false;
        }
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
