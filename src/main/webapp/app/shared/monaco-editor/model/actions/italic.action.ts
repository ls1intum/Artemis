import { faItalic } from '@fortawesome/free-solid-svg-icons';
import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { TextEditor } from './adapter/text-editor.interface';
import { TextEditorKeyCode, TextEditorKeyModifier, TextEditorKeybinding } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-keybinding.model';

const ITALIC_DELIMITER = '*';

/**
 * Action to toggle italic text in the editor. It wraps the selected text with the italic delimiter, e.g. switching between text and *text*.
 */
export class ItalicAction extends TextEditorAction {
    static readonly ID = 'italic.action';
    constructor() {
        super(ItalicAction.ID, 'artemisApp.multipleChoiceQuestion.editor.italic', faItalic, [new TextEditorKeybinding(TextEditorKeyCode.KeyI, TextEditorKeyModifier.CtrlCmd)]);
    }

    /**
     * Toggles the italic delimiter around the selected text in the editor. If the selected text is already italic, the delimiter is removed.
     * If no text is selected, the delimiter is inserted at the current cursor position.
     * @param editor The editor in which to toggle italic text.
     */
    run(editor: TextEditor): void {
        this.toggleDelimiterAroundSelection(editor, ITALIC_DELIMITER, ITALIC_DELIMITER);
        editor.focus();
    }
}
