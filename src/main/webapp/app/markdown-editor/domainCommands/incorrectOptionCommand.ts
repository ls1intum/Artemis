import { DomainCommand } from 'app/markdown-editor/domainCommands/domainCommand';

export class IncorrectOptionCommand extends DomainCommand {

    buttonTranslationString = 'arTeMiSApp.multipleChoiceQuestion.editor.addInCorrectAnswerOption';

    /**
     * @function execute
     * @desc Add a new incorrect answer option to the text editor at the location of the cursor
     */
    execute(): void {
        const addedText = '\n' + this.getOpeningIdentifier() + 'Enter a wrong answer option here' + this.getClosingIdentifier();
        this.editor.focus();
        this.editor.clearSelection();
        this.editor.moveCursorTo(this.editor.getCursorPosition().row, Number.POSITIVE_INFINITY);
        this.editor.insert(addedText);
        const range = this.editor.selection.getRange();
        range.setStart(range.start.row, 6);
        this.editor.selection.setRange(range);
    }

    /**
     * @function getOpeningIdentifier
     * @desc identify the start of the correct option
     */
    getOpeningIdentifier(): string {
        return '[wrong]';
    }

    /**
     * @function getClosingIdentifier
     * @desc identify the end of the correct option
     */
    getClosingIdentifier(): string {
        return '[/wrong]';
    }
}
