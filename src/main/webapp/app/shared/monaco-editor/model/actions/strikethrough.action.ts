import { faStrikethrough } from '@fortawesome/free-solid-svg-icons';
import { TextStyleTextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';

const STRIKETHROUGH_DELIMITER = '~~';

/**
 * Action to toggle strikethrough text in the editor.
 */
export class StrikethroughAction extends TextStyleTextEditorAction {
    static readonly ID = 'strikethrough.action';

    constructor() {
        super(StrikethroughAction.ID, 'artemisApp.multipleChoiceQuestion.editor.strikethrough', faStrikethrough, undefined);
    }

    /**
     * Toggles the strikethrough delimiter around the selected text in the editor. If the selected text is already strikethrough, the delimiter is removed.
     * If no text is selected, the delimiter is inserted at the current cursor position.
     * @param editor The editor in which to toggle strikethrough text.
     */
    run(editor: TextEditor) {
        this.toggleDelimiterAroundSelection(editor, STRIKETHROUGH_DELIMITER, STRIKETHROUGH_DELIMITER);
        editor.focus();
    }
}
