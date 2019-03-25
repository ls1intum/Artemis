import { DomainCommand } from 'app/markdown-editor/domainCommands/domainCommand';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';

export class HintCommand extends DomainCommand {

    buttonTranslationString = 'arTeMiSApp.multipleChoiceQuestion.editor.addHint';

    /**
     * @function execute
     * @desc Add a new hint to the answer option or question title in the editor at the location of the cursor
     */
    execute(): void {
        const text = '\n\t' + this.getOpeningIdentifier() + HintCommand.text;
        ArtemisMarkdown.addTextAtCursor(text, this.aceEditorContainer);
    }

    public static readonly identifier = '[hint]';
    public static readonly text = ' Add a hint here (visible during the quiz via ?-Button)';

    /**
     * @function getOpeningIdentifier
     * @desc identify the start of the hint
     */
    getOpeningIdentifier(): string {
        return HintCommand.identifier;
    }

    /**
     * @function getClosingIdentifier
     * @desc identify the end of the hint
     */
    getClosingIdentifier(): string {
        return '[/hint]';
    }
}
