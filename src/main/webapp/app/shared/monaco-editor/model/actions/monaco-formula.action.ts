import { MonacoEditorDelimiterAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-delimiter-action.model';

const FORMULA_DELIMITER = '$$';
export class MonacoFormulaAction extends MonacoEditorDelimiterAction {
    static readonly ID = 'monaco-formula.action';
    constructor(label: string, translationKey: string) {
        super(MonacoFormulaAction.ID, label, translationKey, undefined, undefined, FORMULA_DELIMITER, FORMULA_DELIMITER);
    }
}
