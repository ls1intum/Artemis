import { faImage } from '@fortawesome/free-solid-svg-icons';
import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';

interface AttachmentArguments {
    text: string;
    url: string;
}

/**
 * Action to insert an attachment into the editor. They follow the format ![text](url).
 */
export class AttachmentAction extends TextEditorAction {
    static readonly ID = 'attachment.action';
    static readonly DEFAULT_INSERT_TEXT = '![](https://)';
    constructor() {
        super(AttachmentAction.ID, 'artemisApp.multipleChoiceQuestion.editor.imageUpload', faImage, undefined);
    }

    /**
     * Executes the action in the current editor with the given arguments (url and text).
     * @param args The text and url of the attachment to insert. If one or both are not provided, the default text will be inserted.
     */
    executeInCurrentEditor(args?: AttachmentArguments): void {
        super.executeInCurrentEditor(args);
    }

    /**
     * Inserts, at the current selection, the attachment markdown with the given text and url if they were provided, or the default text otherwise.
     * @param editor The editor in which to insert the attachment.
     * @param args The text and url of the attachment to insert. If one or both are not provided, the default text will be inserted.
     */
    run(editor: TextEditor, args?: AttachmentArguments): void {
        if (!args?.text || !args?.url) {
            this.replaceTextAtCurrentSelection(editor, AttachmentAction.DEFAULT_INSERT_TEXT);
        } else {
            this.replaceTextAtCurrentSelection(editor, `![${args.text}](${args.url})`);
        }
        editor.focus();
    }
}
