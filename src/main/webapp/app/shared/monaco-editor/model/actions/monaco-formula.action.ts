import { MonacoEditorDelimiterAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-delimiter-action.model';

const FORMULA_DELIMITER = '$$';
export class MonacoFormulaAction extends MonacoEditorDelimiterAction {
    constructor(label: string, translationKey: string) {
        super('monaco-formula.action', label, translationKey, undefined, FORMULA_DELIMITER, FORMULA_DELIMITER);
    }
}
