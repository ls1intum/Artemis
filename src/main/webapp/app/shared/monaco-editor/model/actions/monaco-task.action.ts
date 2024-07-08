import * as monaco from 'monaco-editor';
import { MonacoEditorDomainAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-domain-action.model';

/**
 * Action to insert a task into the editor. They follow the format [task][Task Short Description](testCaseName).
 */
export class MonacoTaskAction extends MonacoEditorDomainAction {
    static readonly ID = 'monaco-task.action';
    static readonly INSERT_TASK_TEXT = '[task][Task Short Description](testCaseName)\n';
    constructor() {
        super(MonacoTaskAction.ID, 'artemisApp.programmingExercise.problemStatement.taskCommand', undefined, undefined);
    }

    /**
     * Inserts, at the current selection, the markdown task.
     * @param editor The editor in which to insert the task.
     */
    run(editor: monaco.editor.ICodeEditor): void {
        this.replaceTextAtCurrentSelection(editor, MonacoTaskAction.INSERT_TASK_TEXT);
        editor.focus();
    }
}
