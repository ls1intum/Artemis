import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { DomainTagCommand } from 'app/markdown-editor/domainCommands/domainTag.command';
import { CreditsCommand } from 'app/markdown-editor/domainCommands/credits.command';
import { InstructionCommand } from 'app/markdown-editor/domainCommands/instruction.command';
import { FeedbackCommand } from 'app/markdown-editor/domainCommands/feedback.command';
import { UsageCountCommand } from 'app/markdown-editor/domainCommands/usageCount.command';

export class GradingCriteriaCommand extends DomainTagCommand {
    creditsCommand = new CreditsCommand();
    instructionCommand = new InstructionCommand();
    feedbackCommand = new FeedbackCommand();
    usageCountCommand = new UsageCountCommand();

    public static readonly identifier = CreditsCommand.identifier + InstructionCommand.identifier + FeedbackCommand.identifier + UsageCountCommand.identifier;
    // public static readonly text = ' Add grading instruction here';

    buttonTranslationString = 'assessmentInstructions.instructions.editor.addInstruction';

    /**
     * @function execute
     * @desc Add a new hint to the answer option or question title in the editor at the location of the cursor
     */
    execute(): void {
        const text =
            '\n' +
            '\n' +
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
            UsageCountCommand.text;

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
        return (
            this.creditsCommand.getClosingIdentifier() +
            this.instructionCommand.getClosingIdentifier() +
            this.feedbackCommand.getClosingIdentifier() +
            this.usageCountCommand.getClosingIdentifier()
        );
    }
}
