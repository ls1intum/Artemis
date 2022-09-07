import { DomainTagCommand } from 'app/shared/markdown-editor/domainCommands/domainTag.command';
import { addTextAtCursor } from 'app/shared/util/markdown.util';

export class InstructionDescriptionCommand extends DomainTagCommand {
    public static readonly identifier = '[description]';
    public static readonly text = 'Add grading instruction here (only visible for tutors)';
    displayCommandButton = false;

    buttonTranslationString = 'artemisApp.assessmentInstructions.instructions.editor.addInstruction';

    /**
     * @function execute
     * @desc Add a new description of the instruction in the editor at the location of the cursor
     */
    execute(): void {
        const text = '\n' + this.getOpeningIdentifier() + InstructionDescriptionCommand.text;
        addTextAtCursor(text, this.aceEditor);
    }

    /**
     * @function getOpeningIdentifier
     * @desc identify the start of the instruction description
     */
    getOpeningIdentifier(): string {
        return InstructionDescriptionCommand.identifier;
    }

    /**
     * @function getClosingIdentifier
     * @desc identify the end of the instruction description
     */
    getClosingIdentifier(): string {
        return '[/description]';
    }
}
