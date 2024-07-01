import { DomainTagCommand } from 'app/shared/markdown-editor/domainCommands/domainTag.command';
import { addTextAtCursor } from 'app/shared/util/markdown.util';

export class CreditsCommand extends DomainTagCommand {
    public static readonly IDENTIFIER = '[credits]';
    public static readonly TEXT = '0';
    // ' Add points students should get for this instruction here';
    displayCommandButton = false;

    buttonTranslationString = 'artemisApp.assessmentInstructions.instructions.editor.addCredits';

    /**
     * @function execute
     * @desc Add a credits for the corresponding instruction in the editor at the location of the cursor
     */
    execute(): void {
        const text = '\n' + this.getOpeningIdentifier() + CreditsCommand.TEXT;
        addTextAtCursor(text, this.aceEditor);
    }

    /**
     * @function getOpeningIdentifier
     * @desc identify the start of the credits
     */
    getOpeningIdentifier(): string {
        return CreditsCommand.IDENTIFIER;
    }

    /**
     * @function getClosingIdentifier
     * @desc identify the end of the credits
     */
    getClosingIdentifier(): string {
        return '[/credits]';
    }
}
