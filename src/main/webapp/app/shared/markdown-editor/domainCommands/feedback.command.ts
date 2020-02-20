import { DomainTagCommand } from 'app/shared/markdown-editor/domainCommands/domainTag.command';
import { ArtemisMarkdown } from 'app/shared/markdown.service';

export class FeedbackCommand extends DomainTagCommand {
    public static readonly identifier = '[feedback]';
    public static readonly text = ' Add feedback for students here (visible for students)';

    buttonTranslationString = 'assessmentInstructions.instructions.editor.addFeedback';

    /**
     * @function execute
     * @desc Add a new hint to the answer option or question title in the editor at the location of the cursor
     */
    execute(): void {
        const text = '\n' + this.getOpeningIdentifier() + FeedbackCommand.text;
        ArtemisMarkdown.addTextAtCursor(text, this.aceEditorContainer);
    }

    /**
     * @function getOpeningIdentifier
     * @desc identify the start of the hint
     */
    getOpeningIdentifier(): string {
        return FeedbackCommand.identifier;
    }

    /**
     * @function getClosingIdentifier
     * @desc identify the end of the hint
     */
    getClosingIdentifier(): string {
        return '[/feedback]';
    }
}
