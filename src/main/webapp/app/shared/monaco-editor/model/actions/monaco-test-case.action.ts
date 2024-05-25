import { MonacoEditorInsertAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-insert-action.model';
import * as monaco from 'monaco-editor';

const INSERT_TEST_CASE_TEXT = 'testCaseName()';
interface TestCaseValue {
    value: string;
    id: string;
}
export class MonacoTestCaseAction extends MonacoEditorInsertAction {
    possibleValues: TestCaseValue[] = [];

    constructor(label: string, translationKey: string) {
        super('monaco-test-case.action', label, translationKey, undefined, undefined, INSERT_TEST_CASE_TEXT);
    }

    run(editor: monaco.editor.ICodeEditor, args?: string) {
        if (!args) {
            super.run(editor);
        } else {
            this.replaceTextAtCurrentSelection(editor, args);
            editor.focus();
        }
    }
}
