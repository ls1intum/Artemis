import * as monaco from 'monaco-editor';
import { MonacoEditorDelimiterAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-delimiter-action.model';
import { faItalic } from '@fortawesome/free-solid-svg-icons';

const ITALIC_DELIMITER = '*';
export class MonacoItalicAction extends MonacoEditorDelimiterAction {
    static readonly ID = 'monaco-italic.action';
    constructor() {
        super(
            MonacoItalicAction.ID,
            'artemisApp.multipleChoiceQuestion.editor.italic',
            faItalic,
            [monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyI],
            ITALIC_DELIMITER,
            ITALIC_DELIMITER,
        );
    }
}
