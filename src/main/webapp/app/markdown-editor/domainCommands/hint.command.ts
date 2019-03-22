import { DomainCommand } from 'app/markdown-editor/domainCommands/domainCommand';

export class HintCommand extends DomainCommand {

    buttonTranslationString = 'arTeMiSApp.multipleChoiceQuestion.editor.addHint';

    /**
     * @function execute
     * @desc Add a new hint to the answer option or question title in the editor at the location of the cursor
     */
    execute(): void {
        const addedText = "\n\t[-h] Add a hint here (visible during the quiz via '?'-Button)";
        this.editor.focus();
        this.editor.clearSelection();
        this.editor.moveCursorTo(this.editor.getCursorPosition().row, Number.POSITIVE_INFINITY);
        this.editor.insert(addedText);
        const range = this.editor.selection.getRange();
        range.setStart(range.start.row, 6);
        this.editor.selection.setRange(range);
    }

    /**
     * @function getIdentifier
     * @desc Identify the hint by the identifier
     */
    getIdentifier(): string {
        return 'h]';
    }
}
