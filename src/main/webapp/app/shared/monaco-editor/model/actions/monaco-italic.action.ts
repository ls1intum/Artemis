import * as monaco from 'monaco-editor';
import { faItalic } from '@fortawesome/free-solid-svg-icons';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';

const ITALIC_DELIMITER = '*';

/**
 * Action to toggle italic text in the editor. It wraps the selected text with the italic delimiter, e.g. switching between text and *text*.
 */
export class MonacoItalicAction extends MonacoEditorAction {
    static readonly ID = 'monaco-italic.action';
    constructor() {
        super(MonacoItalicAction.ID, 'artemisApp.multipleChoiceQuestion.editor.italic', faItalic, [monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyI]);
    }

    /**
     * Toggles the italic delimiter around the selected text in the editor. If the selected text is already italic, the delimiter is removed.
     * If no text is selected, the delimiter is inserted at the current cursor position.
     * @param editor The editor in which to toggle italic text.
     */
    run(editor: monaco.editor.ICodeEditor): void {
        this.toggleDelimiterAroundSelection(editor, ITALIC_DELIMITER, ITALIC_DELIMITER);
        editor.focus();
    }
}
