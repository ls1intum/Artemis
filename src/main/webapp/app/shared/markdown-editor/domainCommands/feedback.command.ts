import { DomainTagCommand } from 'app/shared/markdown-editor/domainCommands/domainTag.command';
import { addTextAtCursor } from 'app/shared/util/markdown-util';

export class FeedbackCommand extends DomainTagCommand {
    public static readonly identifier = '[feedback]';
    public static readonly text = ' Add feedback for students here (visible for students)';

    buttonTranslationString = 'assessmentInstructions.instructions.editor.addFeedback';
    displayCommandButton = false;

    /**
     * Add new feedback for the corresponding instruction in the editor at the location of the cursor
     */
    execute(): void {
        const text = '\n' + this.getOpeningIdentifier() + FeedbackCommand.text;
        addTextAtCursor(text, this.aceEditor);
    }

    getOpeningIdentifier(): string {
        return FeedbackCommand.identifier;
    }

    getClosingIdentifier(): string {
        return '[/feedback]';
    }
}
