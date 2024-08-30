import { MonacoEditorDomainAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-domain-action.model';
import { escapeStringForUseInRegex } from 'app/shared/util/global.utils';
import { MonacoEditorWithActions } from 'app/shared/monaco-editor/model/actions/monaco-editor.util';

/**
 * Action to insert a task into the editor. They follow the format [task][Task Short Description](testCaseName).
 */
export class MonacoTaskAction extends MonacoEditorDomainAction {
    static readonly ID = 'monaco-task.action';
    static readonly TEXT = '[Task Short Description](testCaseName)\n';
    static readonly IDENTIFIER = '[task]';
    static readonly GLOBAL_TASK_REGEX = new RegExp(`${escapeStringForUseInRegex(MonacoTaskAction.IDENTIFIER)}(.*)`, 'g');

    constructor() {
        super(MonacoTaskAction.ID, 'artemisApp.programmingExercise.problemStatement.taskCommand', undefined, undefined);
    }

    /**
     * Inserts, at the current selection, the markdown task.
     * @param editor The editor in which to insert the task.
     */
    run(editor: MonacoEditorWithActions): void {
        this.replaceTextAtCurrentSelection(editor, `${this.getOpeningIdentifier()}${MonacoTaskAction.TEXT}`);
        editor.focus();
    }

    getOpeningIdentifier(): string {
        return MonacoTaskAction.IDENTIFIER;
    }
}
