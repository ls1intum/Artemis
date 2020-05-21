import { addTextAtCursor } from 'app/shared/util/markdown-util';
import { DomainTagCommand } from 'app/shared/markdown-editor/domainCommands/domainTag.command';

export class HintCommand extends DomainTagCommand {
    public static readonly identifier = '[hint]';
    public static readonly text = ' Add a hint here (visible during the quiz via ?-Button)';

    buttonTranslationString = 'artemisApp.multipleChoiceQuestion.editor.addHint';

    /**
     * Add a new hint to the answer option or question title in the editor at the location of the cursor
     */
    execute(): void {
        const text = '\n\t' + this.getOpeningIdentifier() + HintCommand.text;
        addTextAtCursor(text, this.aceEditor);
    }

    // tslint:disable-next-line:completed-docs
    getOpeningIdentifier(): string {
        return HintCommand.identifier;
    }

    // tslint:disable-next-line:completed-docs
    getClosingIdentifier(): string {
        return '[/hint]';
    }
}
