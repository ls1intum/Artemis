import { DomainTagCommand } from 'app/shared/markdown-editor/domainCommands/domainTag.command';
import { addTextAtCursor } from 'app/shared/util/markdown-util';

export class UsageCountCommand extends DomainTagCommand {
    public static readonly identifier = '[maxCountInScore]';
    public static readonly text = ' 0';
    // 'Add how often the credits should be taken into consideration for this instruction: 0 -> the credits should be added as often as the instruction occurs' +
    // ' x of type int -> credits will be added x times only, if instruction occurs more than x times it will not be counted and instead marked as subsequent fault';

    buttonTranslationString = 'assessmentInstructions.instructions.editor.addCountUsage';
    displayCommandButton = false;

    /**
     * Add a new usage count for the corresponding instruction in the editor at the location of the cursor
     */
    execute(): void {
        const text = '\n' + this.getOpeningIdentifier() + UsageCountCommand.text;
        addTextAtCursor(text, this.aceEditor);
    }

    getOpeningIdentifier(): string {
        return UsageCountCommand.identifier;
    }

    getClosingIdentifier(): string {
        return '[/maxCountInScore]';
    }
}
