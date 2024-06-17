import { faImage } from '@fortawesome/free-solid-svg-icons';
import { MonacoEditorInsertAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-insert-action.model';
import * as monaco from 'monaco-editor';

const INSERT_ATTACHMENT_TEXT = '![](https://)';
export class MonacoAttachmentAction extends MonacoEditorInsertAction {
    static readonly ID = 'monaco-attachment.action';
    constructor() {
        super(MonacoAttachmentAction.ID, 'artemisApp.multipleChoiceQuestion.editor.imageUpload', faImage, undefined, INSERT_ATTACHMENT_TEXT);
    }

    run(editor: monaco.editor.ICodeEditor, args?: { text: string; url: string }): void {
        if (!args?.text || !args?.url) {
            super.run(editor);
        } else {
            this.replaceTextAtCurrentSelection(editor, `![${args.text}](${args.url})`);
            editor.focus();
        }
    }
}
