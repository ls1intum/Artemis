import * as monaco from 'monaco-editor';
import { MonacoEditorDomainAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-domain-action.model';

const FORMULA_OPEN_DELIMITER = '$$ ';
const FORMULA_CLOSE_DELIMITER = ' $$';
export class MonacoFormulaAction extends MonacoEditorDomainAction {
    static readonly ID = 'monaco-formula.action';
    static readonly DEFAULT_FORMULA = 'e^{\\frac{1}{4} y^2}';
    constructor() {
        super(MonacoFormulaAction.ID, 'artemisApp.markdownEditor.commands.katex', undefined, undefined);
    }

    run(editor: monaco.editor.ICodeEditor) {
        this.toggleDelimiterAroundSelection(editor, FORMULA_OPEN_DELIMITER, FORMULA_CLOSE_DELIMITER, MonacoFormulaAction.DEFAULT_FORMULA);
        editor.focus();
    }
}
