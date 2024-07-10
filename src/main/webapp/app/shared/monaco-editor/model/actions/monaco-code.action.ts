import * as monaco from 'monaco-editor';
import { faCode } from '@fortawesome/free-solid-svg-icons';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';

const CODE_DELIMITER = '`';

/**
 * Action to toggle code text in the editor. It wraps the selected text with the code delimiter, e.g. switching between text and `text`.
 */
export class MonacoCodeAction extends MonacoEditorAction {
    static readonly ID = 'monaco-code.action';
    constructor() {
        super(MonacoCodeAction.ID, 'artemisApp.multipleChoiceQuestion.editor.code', faCode, undefined);
    }

    /**
     * Toggles the code delimiter around the selected text in the editor. If the selected text is already wrapped in code delimiters, the delimiter is removed.
     * If no text is selected, the code delimiter is inserted at the current cursor position.
     * @param editor The editor in which to toggle code text.
     */
    run(editor: monaco.editor.ICodeEditor) {
        this.toggleDelimiterAroundSelection(editor, CODE_DELIMITER, CODE_DELIMITER);
        editor.focus();
    }
}
