import { DomainMultiOptionCommand } from 'app/markdown-editor/domainCommands';

export class TestCaseCommand extends DomainMultiOptionCommand {
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
