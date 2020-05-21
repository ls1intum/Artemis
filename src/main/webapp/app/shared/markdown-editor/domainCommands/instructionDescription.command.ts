import { DomainTagCommand } from 'app/shared/markdown-editor/domainCommands/domainTag.command';
import { addTextAtCursor } from 'app/shared/util/markdown-util';

export class InstructionDescriptionCommand extends DomainTagCommand {
    public static readonly identifier = '[description]';
    public static readonly text = ' Add grading instruction here (only visible for tutors)';
    displayCommandButton = false;

    buttonTranslationString = 'assessmentInstructions.instructions.editor.addInstruction';

    /**
     * Add a new description of the instruction in the editor at the location of the cursor
     */
    execute(): void {
        const text = '\n' + this.getOpeningIdentifier() + InstructionDescriptionCommand.text;
        addTextAtCursor(text, this.aceEditor);
    }

    // tslint:disable-next-line:completed-docs
    getOpeningIdentifier(): string {
        return InstructionDescriptionCommand.identifier;
    }

    // tslint:disable-next-line:completed-docs
    getClosingIdentifier(): string {
        return '[/description]';
    }
}
