import { faQuoteLeft } from '@fortawesome/free-solid-svg-icons';
import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';

const QUOTE_OPEN_DELIMITER = '> ';

/**
 * Action to toggle quote text in the editor. It wraps the selected text with the quote delimiter, e.g. switching between text and > text.
 */
export class QuoteAction extends TextEditorAction {
    static readonly ID = 'quote.action';
    constructor() {
        super(QuoteAction.ID, 'artemisApp.multipleChoiceQuestion.editor.quote', faQuoteLeft, undefined);
    }

    /**
     * Toggles the quote delimiter around the selected text in the editor. If the selected text is already quoted, the delimiter is removed.
     * @param editor The editor in which to toggle quote text.
     */
    run(editor: TextEditor): void {
        this.toggleDelimiterAroundSelection(editor, QUOTE_OPEN_DELIMITER, '');
        editor.focus();
    }
}
