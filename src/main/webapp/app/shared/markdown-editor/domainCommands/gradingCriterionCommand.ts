import { DomainTagCommand } from 'app/shared/markdown-editor/domainCommands/domainTag.command';
import { addTextAtCursor } from 'app/shared/util/markdown-util';
import { GradingInstructionCommand } from 'app/shared/markdown-editor/domainCommands/gradingInstruction.command';

export class GradingCriterionCommand extends DomainTagCommand {
    public static readonly identifier = '[gradingCriterion]';
    public static readonly text = ' Add criteria title (only visible for tutors)';

    buttonTranslationString = 'assessmentInstructions.instructions.editor.addCriterion';
    displayCommandButton = true;
    gradingInstructionCommand = new GradingInstructionCommand();

    /**
     * Add a new criterion for the corresponding exercise in the editor at the location of the cursor
     */
    execute(): void {
        const text = '\n' + this.getOpeningIdentifier() + GradingCriterionCommand.text + '\n' + this.gradingInstructionCommand.instructionText();
        addTextAtCursor(text, this.aceEditor);
    }

    getOpeningIdentifier(): string {
        return GradingCriterionCommand.identifier;
    }

    getClosingIdentifier(): string {
        return '[/gradingCriterion]';
    }
}
