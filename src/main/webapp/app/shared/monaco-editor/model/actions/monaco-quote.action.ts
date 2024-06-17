import { faQuoteLeft } from '@fortawesome/free-solid-svg-icons';
import { MonacoEditorInsertAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-insert-action.model';

// TODO: Special behavior when selecting text.
const INSERT_QUOTE_TEXT = '> Quote';
export class MonacoQuoteAction extends MonacoEditorInsertAction {
    static readonly ID = 'monaco-quote.action';
    constructor(translationKey: string) {
        super(MonacoQuoteAction.ID, translationKey, faQuoteLeft, undefined, INSERT_QUOTE_TEXT);
    }
}
