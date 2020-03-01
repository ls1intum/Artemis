import { DomainMultiOptionListCommand } from 'app/shared/markdown-editor/domainCommands/domain-multi-option-list.command';

export class TestCaseCommand extends DomainMultiOptionListCommand {
    buttonTranslationString = 'artemisApp.programmingExercise.problemStatement.testCaseCommand';

    protected getValueMeta(): string {
        return 'testCase';
    }

    /**
     * @function getOpeningIdentifier
     * @desc identify the start of the task
     */
    getOpeningIdentifier(): string {
        return '(';
    }

    /**
     * @function getClosingIdentifier
     * @desc identify the end of the explanation
     */
    getClosingIdentifier(): string {
        return ')';
    }
}
