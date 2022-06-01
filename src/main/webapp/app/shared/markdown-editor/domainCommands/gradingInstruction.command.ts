import { DomainTagCommand } from 'app/shared/markdown-editor/domainCommands/domainTag.command';
import { UsageCountCommand } from 'app/shared/markdown-editor/domainCommands/usageCount.command';
import { FeedbackCommand } from 'app/shared/markdown-editor/domainCommands/feedback.command';
import { CreditsCommand } from 'app/shared/markdown-editor/domainCommands/credits.command';
import { addTextAtCursor } from 'app/shared/util/markdown.util';
import { GradingScaleCommand } from 'app/shared/markdown-editor/domainCommands/gradingScaleCommand';
import { InstructionDescriptionCommand } from 'app/shared/markdown-editor/domainCommands/instructionDescription.command';

export class GradingInstructionCommand extends DomainTagCommand {
    creditsCommand = new CreditsCommand();
    gradingScaleCommand = new GradingScaleCommand();
    instructionCommand = new InstructionDescriptionCommand();
    feedbackCommand = new FeedbackCommand();
    usageCountCommand = new UsageCountCommand();

    public static readonly identifier = '[instruction]';
    // public static readonly text = ' Add grading instruction here';

    buttonTranslationString = 'artemisApp.assessmentInstructions.instructions.editor.addInstruction';

    instructionText(): string {
        return (
            this.getOpeningIdentifier() +
            '\n' +
            '\t' +
            (this.creditsCommand.getOpeningIdentifier() +
                CreditsCommand.text +
                '\n' +
                '\t' +
                this.gradingScaleCommand.getOpeningIdentifier() +
                GradingScaleCommand.text +
                '\n' +
                '\t' +
                this.instructionCommand.getOpeningIdentifier() +
                InstructionDescriptionCommand.text +
                '\n' +
                '\t' +
                this.feedbackCommand.getOpeningIdentifier() +
                FeedbackCommand.text +
                '\n' +
                '\t' +
                this.usageCountCommand.getOpeningIdentifier() +
                UsageCountCommand.text) +
            '\n'
        );
    }
    /**
     * @function execute
     * @desc Add a new grading instruction in the editor at the location of the cursor
     */
    execute(): void {
        const text = this.instructionText();

        addTextAtCursor(text, this.aceEditor);
    }

    /**
     * @function getOpeningIdentifier
     * @desc identify the start of the grading instruction
     */
    getOpeningIdentifier(): string {
        return GradingInstructionCommand.identifier;
    }

    /**
     * @function getClosingIdentifier
     * @desc identify the end of the grading instruction
     */
    getClosingIdentifier(): string {
        return '[/instruction]';
    }
}
