import { MultiOptionCommand } from 'app/markdown-editor/domainCommands';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';

export class TestCaseCommand extends MultiOptionCommand {
    buttonTranslationString = 'arTeMiSApp.programmingExercise.problemStatement.testCaseCommand';

    /**
     * @function execute
     * @desc Add a new explanation to answer option or question title in the text editor at the location of the cursor
     */
    execute(value: string): void {
        const text = `(${value})`;
        this.insertText(text);
    }

    /**
     * @function getOpeningIdentifier
     * @desc identify the start of the task
     */
    getOpeningIdentifier(): string {
        return '';
    }

    /**
     * @function getClosingIdentifier
     * @desc identify the end of the explanation
     */
    getClosingIdentifier(): string {
        return '';
    }
}
