import { MonacoEditorInsertAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-insert-action.model';

const INSERT_QUOTE_TEXT = '> Quote';
export class MonacoQuoteAction extends MonacoEditorInsertAction {
    constructor(label: string, translationKey: string) {
        super('monaco-quote.action', label, translationKey, undefined, INSERT_QUOTE_TEXT);
    }
}
