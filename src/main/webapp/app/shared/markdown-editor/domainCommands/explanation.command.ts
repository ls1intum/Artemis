import { addTextAtCursor } from 'app/shared/util/markdown.util';
import { DomainTagCommand } from 'app/shared/markdown-editor/domainCommands/domainTag.command';

export const explanationCommandIdentifier = '[exp]';

export class ExplanationCommand extends DomainTagCommand {
    public static readonly text = ' Add an explanation here (only visible in feedback after quiz has ended)';

    buttonTranslationString = 'artemisApp.multipleChoiceQuestion.editor.addExplanation';

    /**
     * @function execute
     * @desc Add a new explanation to answer option or question title in the text editor at the location of the cursor
     */
    execute(): void {
        const text = '\n\t' + this.getOpeningIdentifier() + ExplanationCommand.text;
        addTextAtCursor(text, this.aceEditor);
    }

    /**
     * @function getOpeningIdentifier
     * @desc identify the start of the explanation
     */
    getOpeningIdentifier(): string {
        return explanationCommandIdentifier;
    }

    /**
     * @function getClosingIdentifier
     * @desc identify the end of the explanation
     */
    getClosingIdentifier(): string {
        return '[/exp]';
    }
}
