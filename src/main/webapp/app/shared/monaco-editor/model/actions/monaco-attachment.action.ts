import { MonacoEditorInsertAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-insert-action.model';

const INSERT_ATTACHMENT_TEXT = '![](https://)';
export class MonacoAttachmentAction extends MonacoEditorInsertAction {
    constructor(label: string, translationKey: string) {
        super('monaco-attachment.action', label, translationKey, undefined, INSERT_ATTACHMENT_TEXT);
    }
}
