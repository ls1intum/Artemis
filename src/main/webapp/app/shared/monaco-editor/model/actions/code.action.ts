import { faCode } from '@fortawesome/free-solid-svg-icons';
import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';

const CODE_DELIMITER = '`';

/**
 * Action to toggle code text in the editor. It wraps the selected text with the code delimiter, e.g. switching between text and `text`.
 */
export class CodeAction extends TextEditorAction {
    static readonly ID = 'code.action';
    constructor() {
        super(CodeAction.ID, 'artemisApp.multipleChoiceQuestion.editor.code', faCode, undefined);
    }

    /**
     * Toggles the code delimiter around the selected text in the editor. If the selected text is already wrapped in code delimiters, the delimiter is removed.
     * If no text is selected, the code delimiter is inserted at the current cursor position.
     * @param editor The editor in which to toggle code text.
     */
    run(editor: TextEditor) {
        this.toggleDelimiterAroundSelection(editor, CODE_DELIMITER, CODE_DELIMITER);
        editor.focus();
    }
}
