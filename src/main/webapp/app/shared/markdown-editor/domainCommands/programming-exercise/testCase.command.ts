import { DomainMultiOptionListCommand } from 'app/shared/markdown-editor/domainCommands/domain-multi-option-list.command';

export class TestCaseCommand extends DomainMultiOptionListCommand {
    buttonTranslationString = 'artemisApp.programmingExercise.problemStatement.testCaseCommand';

    protected getValueMeta(): string {
        return 'testCase';
    }

    // tslint:disable-next-line:completed-docs
    getOpeningIdentifier(): string {
        return '(';
    }

    // tslint:disable-next-line:completed-docs
    getClosingIdentifier(): string {
        return ')';
    }
}
