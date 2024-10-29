import { TextEditorDomainAction } from 'app/shared/monaco-editor/model/actions/text-editor-domain-action.model';
import { escapeStringForUseInRegex } from 'app/shared/util/global.utils';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';

/**
 * Action to insert a task into the editor. They follow the format [task][Task Short Description](testCaseName).
 */
export class TaskAction extends TextEditorDomainAction {
    static readonly ID = 'task.action';
    static readonly TEXT = '[Task Short Description](testCaseName)\n';
    static readonly IDENTIFIER = '[task]';
    static readonly GLOBAL_TASK_REGEX = new RegExp(`${escapeStringForUseInRegex(TaskAction.IDENTIFIER)}(.*)`, 'g');

    constructor() {
        super(TaskAction.ID, 'artemisApp.programmingExercise.problemStatement.taskCommand', undefined, undefined);
    }

    /**
     * Inserts, at the current selection, the markdown task.
     * @param editor The editor in which to insert the task.
     */
    run(editor: TextEditor): void {
        this.replaceTextAtCurrentSelection(editor, `${this.getOpeningIdentifier()}${TaskAction.TEXT}`);
        editor.focus();
    }

    getOpeningIdentifier(): string {
        return TaskAction.IDENTIFIER;
    }
}
