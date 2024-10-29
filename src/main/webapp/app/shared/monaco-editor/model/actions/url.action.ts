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
    static readonly DEFAULT_INSERT_TEXT = '[](https://)';

    constructor() {
        super(UrlAction.ID, 'artemisApp.multipleChoiceQuestion.editor.link', faLink, undefined);
    }

    /**
     * Executes the action in the current editor with the given arguments (url and text).
     * @param args The text and url of the URL to insert. If one or both are not provided, the default text will be inserted.
     */
    executeInCurrentEditor(args?: UrlArguments): void {
        super.executeInCurrentEditor(args);
    }

    /**
     * Inserts, at the current selection, the markdown URL with the given text and url if they were provided, or the default text otherwise.
     * @param editor The editor in which to insert the URL.
     * @param args The text and url of the URL to insert. If one or both are not provided, the default text will be inserted.
     */
    run(editor: TextEditor, args?: UrlArguments): void {
        if (!args?.text || !args?.url) {
            this.replaceTextAtCurrentSelection(editor, UrlAction.DEFAULT_INSERT_TEXT);
        } else {
            this.replaceTextAtCurrentSelection(editor, `[${sanitizeStringForMarkdownEditor(args.text)}](${args.url})`);
        }
        editor.focus();
    }
}
