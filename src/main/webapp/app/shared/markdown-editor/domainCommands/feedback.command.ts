import { DomainTagCommand } from 'app/shared/markdown-editor/domainCommands/domainTag.command';
import { addTextAtCursor } from 'app/shared/util/markdown.util';

export class FeedbackCommand extends DomainTagCommand {
    public static readonly IDENTIFIER = '[feedback]';
    public static readonly TEXT = 'Add feedback for students here (visible for students)';

    buttonTranslationString = 'artemisApp.assessmentInstructions.instructions.editor.addFeedback';
    displayCommandButton = false;

    /**
     * @function execute
     * @desc Add a new feedback for the corresponding instruction in the editor at the location of the cursor
     */
    execute(): void {
        const text = '\n' + this.getOpeningIdentifier() + FeedbackCommand.TEXT;
        addTextAtCursor(text, this.aceEditor);
    }

    /**
     * @function getOpeningIdentifier
     * @desc identify the start of the feedback
     */
    getOpeningIdentifier(): string {
        return FeedbackCommand.IDENTIFIER;
    }

    /**
     * @function getClosingIdentifier
     * @desc identify the end of the feedback
     */
    getClosingIdentifier(): string {
        return '[/feedback]';
    }
}
