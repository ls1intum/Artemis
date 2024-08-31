import { faUnderline } from '@fortawesome/free-solid-svg-icons';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';
import { KeyCode, KeyModifier } from 'app/shared/monaco-editor/model/actions/monaco-editor.util';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-adapter.model';

const UNDERLINE_OPEN_DELIMITER = '<ins>';
const UNDERLINE_CLOSE_DELIMITER = '</ins>';

/**
 * Action to toggle underline text in the editor. It wraps the selected text with the underline delimiter, e.g. switching between text and <ins>text</ins>.
 */
export class MonacoUnderlineAction extends MonacoEditorAction {
    static readonly ID = 'monaco-underline.action';
    constructor() {
        super(MonacoUnderlineAction.ID, 'artemisApp.multipleChoiceQuestion.editor.underline', faUnderline, [KeyModifier.CtrlCmd | KeyCode.KeyU]);
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
