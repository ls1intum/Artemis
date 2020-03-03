import { DomainTagCommand } from 'app/shared/markdown-editor/domainCommands/domainTag.command';
import { ArtemisMarkdown } from 'app/shared/markdown.service';

export class GradingScaleCommand extends DomainTagCommand {
    public static readonly identifier = '[gradingScale]';
    public static readonly text = ' Add instruction grading scale here (only visible for tutors)';

    buttonTranslationString = 'assessmentInstructions.instructions.editor.addGradingScale';

    /**
     * @function execute
     * @desc Add a new gradingScale to the instruction in the editor at the location of the cursor
     */
    execute(): void {
        const text = '\n' + this.getOpeningIdentifier() + GradingScaleCommand.text;
        ArtemisMarkdown.addTextAtCursor(text, this.aceEditorContainer);
    }

    /**
     * @function getOpeningIdentifier
     * @desc identify the start of the gradingScale
     */
    getOpeningIdentifier(): string {
        return GradingScaleCommand.identifier;
    }

    /**
     * @function getClosingIdentifier
     * @desc identify the end of the gradingScale
     */
    getClosingIdentifier(): string {
        return '[/gradingScale]';
    }
}
