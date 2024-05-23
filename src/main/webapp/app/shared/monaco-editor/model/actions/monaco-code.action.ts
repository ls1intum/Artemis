import { MonacoEditorDelimiterAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-delimiter-action.model';

const CODE_DELIMITER = '`';
export class MonacoCodeAction extends MonacoEditorDelimiterAction {
    constructor(label: string, translationKey: string) {
        super('monaco-code.action', label, translationKey, undefined, CODE_DELIMITER, CODE_DELIMITER);
    }
}
