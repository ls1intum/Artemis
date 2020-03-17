import { DomainTagCommand } from 'app/shared/markdown-editor/domainCommands/domainTag.command';
import { ArtemisMarkdown } from 'app/shared/markdown.service';

export class CreditsCommand extends DomainTagCommand {
    public static readonly identifier = '[credits]';
    public static readonly text = ' 0';
    // ' Add points students should get for this instruction here';
    displayCommandButton = false;

    buttonTranslationString = 'assessmentInstructions.instructions.editor.addCredits';

    /**
     * @function execute
     * @desc Add a credits for the corresponding instruction in the editor at the location of the cursor
     */
    execute(): void {
        const text = '\n' + this.getOpeningIdentifier() + CreditsCommand.text;
        ArtemisMarkdown.addTextAtCursor(text, this.aceEditorContainer);
    }

    /**
     * @function getOpeningIdentifier
     * @desc identify the start of the credits
     */
    getOpeningIdentifier(): string {
        return CreditsCommand.identifier;
    }

    /**
     * @function getClosingIdentifier
     * @desc identify the end of the credits
     */
    getClosingIdentifier(): string {
        return '[/credits]';
    }
}
