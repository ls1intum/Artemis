import { faQuoteLeft } from '@fortawesome/free-solid-svg-icons';
import * as monaco from 'monaco-editor';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';

const QUOTE_OPEN_DELIMITER = '> ';
const DEFAULT_QUOTE_TEXT = 'Quote';
export class MonacoQuoteAction extends MonacoEditorAction {
    static readonly ID = 'monaco-quote.action';
    constructor() {
        super(MonacoQuoteAction.ID, 'artemisApp.multipleChoiceQuestion.editor.quote', faQuoteLeft, undefined);
    }

    run(editor: monaco.editor.ICodeEditor): void {
        this.toggleDelimiterAroundSelection(editor, QUOTE_OPEN_DELIMITER, '', DEFAULT_QUOTE_TEXT);
        editor.focus();
    }
}
