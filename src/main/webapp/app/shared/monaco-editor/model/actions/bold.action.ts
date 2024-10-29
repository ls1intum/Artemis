import { faBold } from '@fortawesome/free-solid-svg-icons';
import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';
import { TextEditorKeyCode, TextEditorKeyModifier, TextEditorKeybinding } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-keybinding.model';

const BOLD_DELIMITER = '**';

/**
 * Action to toggle bold text in the editor. It wraps the selected text with the bold delimiter, e.g. switching between text and **text**.
 */
export class BoldAction extends TextEditorAction {
    static readonly ID = 'bold.action';

    constructor() {
        super(BoldAction.ID, 'artemisApp.multipleChoiceQuestion.editor.bold', faBold, [new TextEditorKeybinding(TextEditorKeyCode.KeyB, TextEditorKeyModifier.CtrlCmd)]);
    }

    /**
     * Toggles the bold delimiter around the selected text in the editor. If the selected text is already bold, the delimiter is removed.
     * If no text is selected, the delimiter is inserted at the current cursor position.
     * @param editor The editor in which to toggle bold text.
     */
    run(editor: TextEditor) {
        this.toggleDelimiterAroundSelection(editor, BOLD_DELIMITER, BOLD_DELIMITER);
        editor.focus();
    }
}
