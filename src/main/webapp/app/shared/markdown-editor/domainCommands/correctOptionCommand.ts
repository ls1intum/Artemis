import { addTextAtCursor } from 'app/shared/util/markdown-util';
import { DomainTagCommand } from 'app/shared/markdown-editor/domainCommands/domainTag.command';

export class CorrectOptionCommand extends DomainTagCommand {
    public static readonly identifier = '[correct]';
    buttonTranslationString = 'artemisApp.multipleChoiceQuestion.editor.addCorrectAnswerOption';

    /**
     * Add a new correct answer option to the text editor at the location of the cursor
     */
    execute(): void {
        const text = '\n' + this.getOpeningIdentifier() + ' Enter a correct answer option here';
        addTextAtCursor(text, this.aceEditor);
    }

    // tslint:disable-next-line:completed-docs
    getOpeningIdentifier(): string {
        return CorrectOptionCommand.identifier;
    }

    // tslint:disable-next-line:completed-docs
    getClosingIdentifier(): string {
        return '[/correct]';
    }
}
