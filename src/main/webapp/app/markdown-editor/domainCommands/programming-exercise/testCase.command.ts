import { MultiOptionCommand } from 'app/markdown-editor/domainCommands';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';

export class TestCaseCommand extends MultiOptionCommand {
    buttonTranslationString = 'arTeMiSApp.programmingExercise.problemStatement.testCaseCommand';

    /**
     * @function execute
     * @desc insert selected testCase value into text
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
