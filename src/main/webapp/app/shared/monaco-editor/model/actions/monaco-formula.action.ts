import * as monaco from 'monaco-editor';
import { MonacoEditorDomainAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-domain-action.model';

const FORMULA_DELIMITER = '$$';
const DEFAULT_FORMULA = ' e^{\\frac{1}{4} y^2} ';
export class MonacoFormulaAction extends MonacoEditorDomainAction {
    static readonly ID = 'monaco-formula.action';
    constructor() {
        super(MonacoFormulaAction.ID, 'artemisApp.markdownEditor.commands.katex', undefined, undefined);
    }

    run(editor: monaco.editor.ICodeEditor) {
        this.toggleDelimiterAroundSelection(editor, FORMULA_DELIMITER, FORMULA_DELIMITER, DEFAULT_FORMULA);
        editor.focus();
    }
}
