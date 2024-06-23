import { faImage } from '@fortawesome/free-solid-svg-icons';
import * as monaco from 'monaco-editor';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';

export class MonacoAttachmentAction extends MonacoEditorAction {
    static readonly ID = 'monaco-attachment.action';
    static readonly DEFAULT_INSERT_TEXT = '![](https://)';
    constructor() {
        super(MonacoAttachmentAction.ID, 'artemisApp.multipleChoiceQuestion.editor.imageUpload', faImage, undefined);
    }

    run(editor: monaco.editor.ICodeEditor, args?: { text: string; url: string }): void {
        if (!args?.text || !args?.url) {
            this.replaceTextAtCurrentSelection(editor, MonacoAttachmentAction.DEFAULT_INSERT_TEXT);
        } else {
            this.replaceTextAtCurrentSelection(editor, `![${args.text}](${args.url})`);
        }
        editor.focus();
    }
}
