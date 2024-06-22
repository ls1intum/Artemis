import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';
import * as monaco from 'monaco-editor';

// TODO: The task insert command also works like an ordered list.
const INSERT_TASK_TEXT = '[task][Task Short Description](testCaseName)\n';
export class MonacoTaskAction extends MonacoEditorAction {
    static readonly ID = 'monaco-task.action';
    constructor() {
        super(MonacoTaskAction.ID, 'artemisApp.programmingExercise.problemStatement.taskCommand', undefined, undefined);
    }

    run(editor: monaco.editor.ICodeEditor): void {
        this.replaceTextAtCurrentSelection(editor, INSERT_TASK_TEXT);
        editor.focus();
    }
}
