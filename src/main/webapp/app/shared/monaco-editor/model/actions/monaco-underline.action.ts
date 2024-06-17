import * as monaco from 'monaco-editor';
import { MonacoEditorDelimiterAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-delimiter-action.model';
import { faUnderline } from '@fortawesome/free-solid-svg-icons';

const UNDERLINE_OPEN_DELIMITER = '<ins>';
const UNDERLINE_CLOSE_DELIMITER = '</ins>';
export class MonacoUnderlineAction extends MonacoEditorDelimiterAction {
    static readonly ID = 'monaco-underline.action';
    constructor() {
        super(
            MonacoUnderlineAction.ID,
            'artemisApp.multipleChoiceQuestion.editor.underline',
            faUnderline,
            [monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyU],
            UNDERLINE_OPEN_DELIMITER,
            UNDERLINE_CLOSE_DELIMITER,
        );
    }
}
