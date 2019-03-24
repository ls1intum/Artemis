import { DomainCommand } from 'app/markdown-editor/domainCommands/domainCommand';

export class ExplanationCommand extends DomainCommand {

    buttonTranslationString = 'arTeMiSApp.multipleChoiceQuestion.editor.addExplanation';

    /**
     * @function execute
     * @desc Add a new explanation to answer option or question title in the text editor at the location of the cursor
     */
    execute(): void {
        const addedText = '\n\t' + this.getOpeningIdentifier() + 'Add an explanation here (only visible in feedback after quiz has ended)' + this.getClosingIdentifier();
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
     * @desc identify the start of the explanation
     */
    getOpeningIdentifier(): string {
        return '[exp]';
    }

    /**
     * @function getClosingIdentifier
     * @desc identify the end of the explanation
     */
    getClosingIdentifier(): string {
        return '[/exp]';
    }
}
