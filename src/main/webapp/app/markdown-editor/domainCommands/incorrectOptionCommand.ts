import { DomainCommand } from 'app/markdown-editor/domainCommands/domainCommand';

export class IncorrectOptionCommand extends DomainCommand {

    buttonTranslationString = 'arTeMiSApp.multipleChoiceQuestion.editor.addInCorrectAnswerOption';

    /**
     * @function execute
     * @desc Add a new incorrect answer option to the text editor at the location of the cursor
     */
    execute(): void {
        const addedText = '\n[- ] Enter an incorrect answer option here';
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
     * @desc Identify the inCorrectOption by the identifier
     */
    getIdentifier(): string {
        return ' ]';
    }
}
