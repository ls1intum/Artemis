import { DomainTagCommand } from 'app/shared/markdown-editor/domainCommands/domainTag.command';
import { ArtemisMarkdown } from 'app/shared/markdown.service';

export class InstructionCommand extends DomainTagCommand {
    public static readonly identifier = '[instruction]';
    public static readonly text = ' Add grading instruction here (only visible for tutors)';

    buttonTranslationString = 'assessmentInstructions.instructions.editor.addInstruction';

    /**
     * @function execute
     * @desc Add a new hint to the answer option or question title in the editor at the location of the cursor
     */
    execute(): void {
        const text = '\n' + this.getOpeningIdentifier() + InstructionCommand.text;
        ArtemisMarkdown.addTextAtCursor(text, this.aceEditorContainer);
    }

    /**
     * @function getOpeningIdentifier
     * @desc identify the start of the hint
     */
    getOpeningIdentifier(): string {
        return InstructionCommand.identifier;
    }

    /**
     * @function getClosingIdentifier
     * @desc identify the end of the hint
     */
    getClosingIdentifier(): string {
        return '[/instruction]';
    }
}
