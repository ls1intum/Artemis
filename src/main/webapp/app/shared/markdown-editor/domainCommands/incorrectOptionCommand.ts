import { addTextAtCursor } from 'app/shared/util/markdown-util';
import { DomainTagCommand } from 'app/shared/markdown-editor/domainCommands/domainTag.command';

export class IncorrectOptionCommand extends DomainTagCommand {
    public static readonly identifier = '[wrong]';
    buttonTranslationString = 'artemisApp.multipleChoiceQuestion.editor.addInCorrectAnswerOption';

    /**
     * Add a new incorrect answer option to the text editor at the location of the cursor
     */
    execute(): void {
        const text = '\n' + this.getOpeningIdentifier() + ' Enter a wrong answer option here';
        addTextAtCursor(text, this.aceEditor);
    }

    getOpeningIdentifier(): string {
        return IncorrectOptionCommand.identifier;
    }

    getClosingIdentifier(): string {
        return '[/wrong]';
    }
}
