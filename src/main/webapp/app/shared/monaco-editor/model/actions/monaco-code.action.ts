import { faCode } from '@fortawesome/free-solid-svg-icons';
import { MonacoEditorDelimiterAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-delimiter-action.model';

const CODE_DELIMITER = '`';
export class MonacoCodeAction extends MonacoEditorDelimiterAction {
    static readonly ID = 'monaco-code.action';
    constructor() {
        super(MonacoCodeAction.ID, 'artemisApp.multipleChoiceQuestion.editor.code', faCode, undefined, CODE_DELIMITER, CODE_DELIMITER);
    }
}
