import { addTextAtCursor } from 'app/shared/util/markdown-util';
import { DomainTagCommand } from 'app/shared/markdown-editor/domainCommands/domainTag.command';

export class ExplanationCommand extends DomainTagCommand {
    public static readonly identifier = '[exp]';
    public static readonly text = ' Add an explanation here (only visible in feedback after quiz has ended)';

    buttonTranslationString = 'artemisApp.multipleChoiceQuestion.editor.addExplanation';

    /**
     * Add a new explanation to answer option or question title in the text editor at the location of the cursor
     */
    execute(): void {
        const text = '\n\t' + this.getOpeningIdentifier() + ExplanationCommand.text;
        addTextAtCursor(text, this.aceEditor);
    }

    // tslint:disable-next-line:completed-docs
    getOpeningIdentifier(): string {
        return ExplanationCommand.identifier;
    }

    // tslint:disable-next-line:completed-docs
    getClosingIdentifier(): string {
        return '[/exp]';
    }
}
