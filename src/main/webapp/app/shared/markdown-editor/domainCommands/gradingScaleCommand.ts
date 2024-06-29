import { DomainTagCommand } from 'app/shared/markdown-editor/domainCommands/domainTag.command';
import { addTextAtCursor } from 'app/shared/util/markdown.util';

export class GradingScaleCommand extends DomainTagCommand {
    public static readonly IDENTIFIER = '[gradingScale]';
    public static readonly TEXT = 'Add instruction grading scale here (only visible for tutors)';
    displayCommandButton = false;

    buttonTranslationString = 'artemisApp.assessmentInstructions.instructions.editor.addGradingScale';

    /**
     * @function execute
     * @desc Add a new gradingScale to the instruction in the editor at the location of the cursor
     */
    execute(): void {
        const text = '\n' + this.getOpeningIdentifier() + GradingScaleCommand.TEXT;
        addTextAtCursor(text, this.aceEditor);
    }

    /**
     * @function getOpeningIdentifier
     * @desc identify the start of the gradingScale
     */
    getOpeningIdentifier(): string {
        return GradingScaleCommand.IDENTIFIER;
    }

    /**
     * @function getClosingIdentifier
     * @desc identify the end of the gradingScale
     */
    getClosingIdentifier(): string {
        return '[/gradingScale]';
    }
}
