import { faQuoteLeft } from '@fortawesome/free-solid-svg-icons';
import * as monaco from 'monaco-editor';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';

const QUOTE_OPEN_DELIMITER = '> ';
const DEFAULT_QUOTE_TEXT = 'Quote';

/**
 * Action to toggle quote text in the editor. It wraps the selected text with the quote delimiter, e.g. switching between text and > text.
 */
export class MonacoQuoteAction extends MonacoEditorAction {
    static readonly ID = 'monaco-quote.action';
    constructor() {
        super(MonacoQuoteAction.ID, 'artemisApp.multipleChoiceQuestion.editor.quote', faQuoteLeft, undefined);
    }

    /**
     * Toggles the quote delimiter around the selected text in the editor. If the selected text is already quoted, the delimiter is removed.
     * @param editor The editor in which to toggle quote text.
     */
    run(editor: monaco.editor.ICodeEditor): void {
        this.toggleDelimiterAroundSelection(editor, QUOTE_OPEN_DELIMITER, '', DEFAULT_QUOTE_TEXT);
        editor.focus();
    }
}
