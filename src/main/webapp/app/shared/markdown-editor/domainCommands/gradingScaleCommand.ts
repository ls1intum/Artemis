import { DomainTagCommand } from 'app/shared/markdown-editor/domainCommands/domainTag.command';
import { addTextAtCursor } from 'app/shared/util/markdown-util';

export class GradingScaleCommand extends DomainTagCommand {
    public static readonly identifier = '[gradingScale]';
    public static readonly text = ' Add instruction grading scale here (only visible for tutors)';
    displayCommandButton = false;

    buttonTranslationString = 'assessmentInstructions.instructions.editor.addGradingScale';

    /**
     * Add a new gradingScale to the instruction in the editor at the location of the cursor
     */
    execute(): void {
        const text = '\n' + this.getOpeningIdentifier() + GradingScaleCommand.text;
        addTextAtCursor(text, this.aceEditor);
    }

    // tslint:disable-next-line:completed-docs
    getOpeningIdentifier(): string {
        return GradingScaleCommand.identifier;
    }

    // tslint:disable-next-line:completed-docs
    getClosingIdentifier(): string {
        return '[/gradingScale]';
    }
}
