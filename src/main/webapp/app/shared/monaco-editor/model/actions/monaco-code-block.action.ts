import * as monaco from 'monaco-editor';
import { faFileCode } from '@fortawesome/free-solid-svg-icons';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';

const CODE_BLOCK_OPEN_DELIMITER = '```java\n';
const CODE_BLOCK_CLOSE_DELIMITER = '\n```';
export class MonacoCodeBlockAction extends MonacoEditorAction {
    static readonly ID = 'monaco-code-block.action';
    constructor() {
        super(MonacoCodeBlockAction.ID, 'artemisApp.multipleChoiceQuestion.editor.codeBlock', faFileCode, undefined);
    }

    run(editor: monaco.editor.ICodeEditor): void {
        this.toggleDelimiterAroundSelection(editor, CODE_BLOCK_OPEN_DELIMITER, CODE_BLOCK_CLOSE_DELIMITER);
        editor.focus();
    }
}
