import { faLink } from '@fortawesome/free-solid-svg-icons';
import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';
import { sanitizeStringForMarkdownEditor } from 'app/shared/util/markdown.util';

interface UrlArguments {
    text: string;
    url: string;
}

/**
 * Action to insert a URL into the editor. They follow the format [text](url).
 */
export class UrlAction extends TextEditorAction {
    static readonly ID = 'url.action';
    static readonly DEFAULT_LINK_TEXT = 'Link';
    static readonly DEFAULT_INSERT_TEXT = `[🔗 ${this.DEFAULT_LINK_TEXT}](https://)`;

    constructor() {
        super(UrlAction.ID, 'artemisApp.multipleChoiceQuestion.editor.link', faLink, undefined);
    }

    /**
     * Executes the action in the current editor with the given arguments (url and text).
     * @param args The text and url of the URL to insert. If one or both are not provided, checks for selected text to wrap.
     */
    executeInCurrentEditor(args?: UrlArguments): void {
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
            // Check if there's selected text
            const selectedText = this.getSelectedText(editor);
            if (selectedText && selectedText.trim()) {
                // Wrap selected text with link syntax
                const selection = editor.getSelection();
                if (selection) {
                    this.replaceTextAtRange(editor, selection, `[${sanitizeStringForMarkdownEditor(selectedText)}](https://)`);
                }
            } else {
                // No selection, insert default text
                this.replaceTextAtCurrentSelection(editor, UrlAction.DEFAULT_INSERT_TEXT);
            }
        } else {
            this.replaceTextAtCurrentSelection(editor, `[${sanitizeStringForMarkdownEditor(args.text)}](${args.url})`);
        }
        editor.focus();
    }
}
