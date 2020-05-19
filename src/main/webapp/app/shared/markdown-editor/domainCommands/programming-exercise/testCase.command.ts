import { DomainMultiOptionListCommand } from 'app/shared/markdown-editor/domainCommands/domain-multi-option-list.command';

export class TestCaseCommand extends DomainMultiOptionListCommand {
    buttonTranslationString = 'artemisApp.programmingExercise.problemStatement.testCaseCommand';

    protected getValueMeta(): string {
        return 'testCase';
    }

    getOpeningIdentifier(): string {
        return '(';
    }

    getClosingIdentifier(): string {
        return ')';
    }
}
