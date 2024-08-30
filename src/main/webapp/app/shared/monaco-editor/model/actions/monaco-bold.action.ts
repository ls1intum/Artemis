import { faBold } from '@fortawesome/free-solid-svg-icons';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';
import { KeyCode, KeyModifier, MonacoEditorWithActions } from 'app/shared/monaco-editor/model/actions/monaco-editor.util';

const BOLD_DELIMITER = '**';

/**
 * Action to toggle bold text in the editor. It wraps the selected text with the bold delimiter, e.g. switching between text and **text**.
 */
export class MonacoBoldAction extends MonacoEditorAction {
    static readonly ID = 'monaco-bold.action';

    constructor() {
        super(MonacoBoldAction.ID, 'artemisApp.multipleChoiceQuestion.editor.bold', faBold, [KeyModifier.CtrlCmd | KeyCode.KeyB]);
    }

    /**
     * Toggles the bold delimiter around the selected text in the editor. If the selected text is already bold, the delimiter is removed.
     * If no text is selected, the delimiter is inserted at the current cursor position.
     * @param editor The editor in which to toggle bold text.
     */
    run(editor: MonacoEditorWithActions) {
        this.toggleDelimiterAroundSelection(editor, BOLD_DELIMITER, BOLD_DELIMITER);
        editor.focus();
    }
}
