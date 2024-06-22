import * as monaco from 'monaco-editor';
import { faBold } from '@fortawesome/free-solid-svg-icons';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';

const BOLD_DELIMITER = '**';
export class MonacoBoldAction extends MonacoEditorAction {
    static readonly ID = 'monaco-bold.action';

    constructor() {
        super(MonacoBoldAction.ID, 'artemisApp.multipleChoiceQuestion.editor.bold', faBold, [monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyB]);
    }

    run(editor: monaco.editor.ICodeEditor) {
        this.toggleDelimiterAroundSelection(editor, BOLD_DELIMITER, BOLD_DELIMITER);
    }
}
