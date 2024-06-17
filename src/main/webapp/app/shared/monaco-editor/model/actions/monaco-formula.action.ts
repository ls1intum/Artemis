import { MonacoEditorDelimiterAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-delimiter-action.model';

const FORMULA_DELIMITER = '$$';
export class MonacoFormulaAction extends MonacoEditorDelimiterAction {
    static readonly ID = 'monaco-formula.action';
    constructor() {
        super(MonacoFormulaAction.ID, 'artemisApp.markdownEditor.commands.katex', undefined, undefined, FORMULA_DELIMITER, FORMULA_DELIMITER);
    }
}
