import { MonacoEditorInsertAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-insert-action.model';

// TODO: The task insert command also works like an ordered list.
const INSERT_TASK_TEXT = '[task][Task Short Description](testCaseName)\n';
export class MonacoTaskAction extends MonacoEditorInsertAction {
    constructor(label: string, translationKey: string) {
        super('monaco-task.action', label, translationKey, undefined, undefined, INSERT_TASK_TEXT);
    }
}
