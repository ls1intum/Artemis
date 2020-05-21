import { DomainTagCommand } from 'app/shared/markdown-editor/domainCommands/domainTag.command';
import { addTextAtCursor } from 'app/shared/util/markdown-util';

export class CreditsCommand extends DomainTagCommand {
    public static readonly identifier = '[credits]';
    public static readonly text = ' 0';
    // ' Add points students should get for this instruction here';
    displayCommandButton = false;

    buttonTranslationString = 'assessmentInstructions.instructions.editor.addCredits';

    /**
     * Add credits for the corresponding instruction in the editor at the location of the cursor
     */
    execute(): void {
        const text = '\n' + this.getOpeningIdentifier() + CreditsCommand.text;
        addTextAtCursor(text, this.aceEditor);
    }

    // tslint:disable-next-line:completed-docs
    getOpeningIdentifier(): string {
        return CreditsCommand.identifier;
    }

    // tslint:disable-next-line:completed-docs
    getClosingIdentifier(): string {
        return '[/credits]';
    }
}
