import * as monaco from 'monaco-editor';
import { faUnderline } from '@fortawesome/free-solid-svg-icons';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';

const UNDERLINE_OPEN_DELIMITER = '<ins>';
const UNDERLINE_CLOSE_DELIMITER = '</ins>';
export class MonacoUnderlineAction extends MonacoEditorAction {
    static readonly ID = 'monaco-underline.action';
    constructor() {
        super(MonacoUnderlineAction.ID, 'artemisApp.multipleChoiceQuestion.editor.underline', faUnderline, [monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyU]);
    }

    run(editor: monaco.editor.ICodeEditor): void {
        this.toggleDelimiterAroundSelection(editor, UNDERLINE_OPEN_DELIMITER, UNDERLINE_CLOSE_DELIMITER);
        editor.focus();
    }
}
