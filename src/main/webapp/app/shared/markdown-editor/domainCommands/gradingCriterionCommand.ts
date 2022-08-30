import { DomainTagCommand } from 'app/shared/markdown-editor/domainCommands/domainTag.command';
import { addTextAtCursor } from 'app/shared/util/markdown.util';
import { GradingInstructionCommand } from 'app/shared/markdown-editor/domainCommands/gradingInstruction.command';

export class GradingCriterionCommand extends DomainTagCommand {
    public static readonly identifier = '[criterion]';
    public static readonly text = ' Add criteria title (only visible for tutors)';

    buttonTranslationString = 'artemisApp.assessmentInstructions.instructions.editor.addCriterion';
    displayCommandButton = true;
    gradingInstructionCommand = new GradingInstructionCommand();

    /**
     * @function execute
     * @desc Add a new criterion for the corresponding exercise in the editor at the location of the cursor
     */
    execute(): void {
        const text = '\n' + this.getOpeningIdentifier() + GradingCriterionCommand.text + '\n' + this.gradingInstructionCommand.instructionText();
        addTextAtCursor(text, this.aceEditor);
    }

    /**
     * @function getOpeningIdentifier
     * @desc identify the start of the criterion
     */
    getOpeningIdentifier(): string {
        return GradingCriterionCommand.identifier;
    }

    /**
     * @function getClosingIdentifier
     * @desc identify the end of the criterion
     */
    getClosingIdentifier(): string {
        return '[/criterion]';
    }
}
