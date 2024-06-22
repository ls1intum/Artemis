import * as monaco from 'monaco-editor';
import { faItalic } from '@fortawesome/free-solid-svg-icons';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';

const ITALIC_DELIMITER = '*';
export class MonacoItalicAction extends MonacoEditorAction {
    static readonly ID = 'monaco-italic.action';
    constructor() {
        super(MonacoItalicAction.ID, 'artemisApp.multipleChoiceQuestion.editor.italic', faItalic, [monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyI]);
    }

    run(editor: monaco.editor.ICodeEditor): void {
        this.toggleDelimiterAroundSelection(editor, ITALIC_DELIMITER, ITALIC_DELIMITER);
        editor.focus();
    }
}
