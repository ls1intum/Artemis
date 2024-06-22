import * as monaco from 'monaco-editor';
import { faCode } from '@fortawesome/free-solid-svg-icons';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';

const CODE_DELIMITER = '`';
export class MonacoCodeAction extends MonacoEditorAction {
    static readonly ID = 'monaco-code.action';
    constructor() {
        super(MonacoCodeAction.ID, 'artemisApp.multipleChoiceQuestion.editor.code', faCode, undefined);
    }

    run(editor: monaco.editor.ICodeEditor) {
        this.toggleDelimiterAroundSelection(editor, CODE_DELIMITER, CODE_DELIMITER);
    }
}
