import { DomainCommand } from 'app/markdown-editor/domainCommands/domainCommand';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';

export class CorrectOptionCommand extends DomainCommand {

    buttonTranslationString = 'arTeMiSApp.multipleChoiceQuestion.editor.addCorrectAnswerOption';

    /**
     * @function execute
     * @desc Add a new correct answer option to the text editor at the location of the cursor
     */
    execute(): void {
        const text = '\n' + this.getOpeningIdentifier() + ' Enter a correct answer option here';
        ArtemisMarkdown.addTextAtCursor(text, this.aceEditorContainer);
    }

    /**
     * @function getOpeningIdentifier
     * @desc identify the start of the correct option
     */
    getOpeningIdentifier(): string {
        return '[correct]';
    }

    /**
     * @function getClosingIdentifier
     * @desc identify the end of the correct option
     */
    getClosingIdentifier(): string {
        return '[/correct]';
    }
}
