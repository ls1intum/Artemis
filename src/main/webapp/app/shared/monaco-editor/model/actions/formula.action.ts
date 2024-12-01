import { TextEditorDomainAction } from 'app/shared/monaco-editor/model/actions/text-editor-domain-action.model';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';

const FORMULA_OPEN_DELIMITER = '$$ ';
const FORMULA_CLOSE_DELIMITER = ' $$';

/**
 * Action to toggle formula text in the editor. It wraps the selected text with the formula delimiter, e.g. switching between text and $$ text $$.
 */
export class FormulaAction extends TextEditorDomainAction {
    static readonly ID = 'formula.action';
    static readonly DEFAULT_FORMULA = 'e^{\\frac{1}{4} y^2}';
    constructor() {
        super(FormulaAction.ID, 'artemisApp.markdownEditor.commands.katex', undefined, undefined);
    }

    /**
     * Toggles the formula delimiter around the selected text in the editor. If the selected text is already a formula, the delimiter is removed.
     * If no text is selected, the default formula is inserted at the current cursor position.
     * @param editor The editor in which to toggle formula text.
     */
    run(editor: TextEditor) {
        this.toggleDelimiterAroundSelection(editor, this.getOpeningIdentifier(), FORMULA_CLOSE_DELIMITER, FormulaAction.DEFAULT_FORMULA);
        editor.focus();
    }

    getOpeningIdentifier(): string {
        return FORMULA_OPEN_DELIMITER;
    }
}
