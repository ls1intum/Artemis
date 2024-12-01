import { faUnderline } from '@fortawesome/free-solid-svg-icons';
import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';
import { TextEditorKeyCode, TextEditorKeyModifier, TextEditorKeybinding } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-keybinding.model';

const UNDERLINE_OPEN_DELIMITER = '<ins>';
const UNDERLINE_CLOSE_DELIMITER = '</ins>';

/**
 * Action to toggle underline text in the editor. It wraps the selected text with the underline delimiter, e.g. switching between text and <ins>text</ins>.
 */
export class UnderlineAction extends TextEditorAction {
    static readonly ID = 'underline.action';
    constructor() {
        super(UnderlineAction.ID, 'artemisApp.multipleChoiceQuestion.editor.underline', faUnderline, [
            new TextEditorKeybinding(TextEditorKeyCode.KeyU, TextEditorKeyModifier.CtrlCmd),
        ]);
    }

    /**
     * Toggles the underline delimiter around the selected text in the editor. If the selected text is already underlined, the delimiter is removed.
     * @param editor The editor in which to toggle underline text.
     */
    run(editor: TextEditor): void {
        this.toggleDelimiterAroundSelection(editor, UNDERLINE_OPEN_DELIMITER, UNDERLINE_CLOSE_DELIMITER);
        editor.focus();
    }
}
