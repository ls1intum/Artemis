import { DomainTagCommand } from 'app/shared/markdown-editor/domainCommands/domainTag.command';
import { InstructionCommand } from 'app/shared/markdown-editor/domainCommands/instruction.command';
import { UsageCountCommand } from 'app/shared/markdown-editor/domainCommands/usageCount.command';
import { FeedbackCommand } from 'app/shared/markdown-editor/domainCommands/feedback.command';
import { CreditsCommand } from 'app/shared/markdown-editor/domainCommands/credits.command';
import { ArtemisMarkdown } from 'app/shared/markdown.service';

export class GradingCriteriaCommand extends DomainTagCommand {
    creditsCommand = new CreditsCommand();
    instructionCommand = new InstructionCommand();
    feedbackCommand = new FeedbackCommand();
    usageCountCommand = new UsageCountCommand();

    public static readonly identifier = '[gradingInstruction]';
    // public static readonly text = ' Add grading instruction here';

    buttonTranslationString = 'assessmentInstructions.instructions.editor.addInstruction';

    /**
     * @function execute
     * @desc Add a new hint to the answer option or question title in the editor at the location of the cursor
     */
    execute(): void {
        const text =
            this.creditsCommand.getOpeningIdentifier() +
            CreditsCommand.text +
            '\n' +
            this.instructionCommand.getOpeningIdentifier() +
            InstructionCommand.text +
            '\n' +
            this.feedbackCommand.getOpeningIdentifier() +
            FeedbackCommand.text +
            '\n' +
            this.usageCountCommand.getOpeningIdentifier() +
            UsageCountCommand.text +
            '\n' +
            this.getOpeningIdentifier();

        ArtemisMarkdown.addTextAtCursor(text, this.aceEditorContainer);
    }

    /**
     * @function getOpeningIdentifier
     * @desc identify the start of the hint
     */
    getOpeningIdentifier(): string {
        return GradingCriteriaCommand.identifier;
    }

    /**
     * @function getClosingIdentifier
     * @desc identify the end of the hint
     */
    getClosingIdentifier(): string {
        return '[/gradingInstruction]';
    }
}
