import { faFileCode } from '@fortawesome/free-solid-svg-icons';
import { MonacoEditorDelimiterAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-delimiter-action.model';

const CODE_BLOCK_OPEN_DELIMITER = '```java\n';
const CODE_BLOCK_CLOSE_DELIMITER = '\n```';
export class MonacoCodeBlockAction extends MonacoEditorDelimiterAction {
    static readonly ID = 'monaco-code-block.action';
    constructor() {
        super(MonacoCodeBlockAction.ID, 'artemisApp.multipleChoiceQuestion.editor.codeBlock', faFileCode, undefined, CODE_BLOCK_OPEN_DELIMITER, CODE_BLOCK_CLOSE_DELIMITER);
    }
}
