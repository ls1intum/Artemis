import { MonacoEditorInsertAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-insert-action.model';

const INSERT_URL_TEXT = '[](https://)';
export class MonacoUrlAction extends MonacoEditorInsertAction {
    constructor(label: string, translationKey: string) {
        super('monaco-url.action', label, translationKey, undefined, INSERT_URL_TEXT);
    }
}
