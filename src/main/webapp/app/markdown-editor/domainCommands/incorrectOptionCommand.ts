import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { DomainTagCommand } from 'app/markdown-editor/domainCommands/domainTag.command';

export class IncorrectOptionCommand extends DomainTagCommand {
    buttonTranslationString = 'arTeMiSApp.multipleChoiceQuestion.editor.addInCorrectAnswerOption';

    /**
     * @function execute
     * @desc Add a new incorrect answer option to the text editor at the location of the cursor
     */
    execute(): void {
        const text = '\n' + this.getOpeningIdentifier() + ' Enter a wrong answer option here';
        ArtemisMarkdown.addTextAtCursor(text, this.aceEditorContainer);
    }

    /**
     * @function getOpeningIdentifier
     * @desc identify the start of the correct option
     */
    getOpeningIdentifier(): string {
        return '[wrong]';
    }

    /**
     * @function getClosingIdentifier
     * @desc identify the end of the correct option
     */
    getClosingIdentifier(): string {
        return '[/wrong]';
    }
}
