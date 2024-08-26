import * as monaco from 'monaco-editor';
import { MonacoEditorDomainAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-domain-action.model';

const FORMULA_OPEN_DELIMITER = '$$ ';
const FORMULA_CLOSE_DELIMITER = ' $$';

/**
 * Action to toggle formula text in the editor. It wraps the selected text with the formula delimiter, e.g. switching between text and $$ text $$.
 */
export class MonacoFormulaAction extends MonacoEditorDomainAction {
    static readonly ID = 'monaco-formula.action';
    static readonly DEFAULT_FORMULA = 'e^{\\frac{1}{4} y^2}';
    constructor() {
        super(MonacoFormulaAction.ID, 'artemisApp.markdownEditor.commands.katex', undefined, undefined);
    }

    /**
     * Toggles the formula delimiter around the selected text in the editor. If the selected text is already a formula, the delimiter is removed.
     * If no text is selected, the default formula is inserted at the current cursor position.
     * @param editor The editor in which to toggle formula text.
     */
    run(editor: monaco.editor.ICodeEditor) {
        this.toggleDelimiterAroundSelection(editor, this.getOpeningIdentifier(), FORMULA_CLOSE_DELIMITER, MonacoFormulaAction.DEFAULT_FORMULA);
        editor.focus();
    }

    getOpeningIdentifier(): string {
        return FORMULA_OPEN_DELIMITER;
    }
}
