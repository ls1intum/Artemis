import { DomainTagCommand } from 'app/shared/markdown-editor/domainCommands/domainTag.command';
import { ArtemisMarkdown } from 'app/shared/markdown.service';

export class UsageCountCommand extends DomainTagCommand {
    public static readonly identifier = '[count]';
    public static readonly text = ' 0';
    // 'Add how often the credits should be taken into consideration for this instruction: 0 -> the credits should be added as often as the instruction occurs' +
    // ' x of type int -> credits will be added x times only, if instruction occurs more than x times it will not be counted and instead marked as subsequent fault';

    buttonTranslationString = 'assessmentInstructions.instructions.editor.addCountUsage';

    /**
     * @function execute
     * @desc Add a new hint to the answer option or question title in the editor at the location of the cursor
     */
    execute(): void {
        const text = '\n' + this.getOpeningIdentifier() + UsageCountCommand.text;
        ArtemisMarkdown.addTextAtCursor(text, this.aceEditorContainer);
    }

    /**
     * @function getOpeningIdentifier
     * @desc identify the start of the hint
     */
    getOpeningIdentifier(): string {
        return UsageCountCommand.identifier;
    }

    /**
     * @function getClosingIdentifier
     * @desc identify the end of the hint
     */
    getClosingIdentifier(): string {
        return '[/count]';
    }
}
