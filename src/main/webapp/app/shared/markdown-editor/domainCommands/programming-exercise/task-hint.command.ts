import { DomainMultiOptionListCommand } from 'app/shared/markdown-editor/domainCommands/domain-multi-option-list.command';

export class TaskHintCommand extends DomainMultiOptionListCommand {
    buttonTranslationString = 'artemisApp.programmingExercise.problemStatement.exerciseHintCommand';

    protected getValueMeta(): string {
        return 'exerciseHint';
    }

    // tslint:disable-next-line:completed-docs
    getOpeningIdentifier(): string {
        return '{';
    }

    // tslint:disable-next-line:completed-docs
    getClosingIdentifier(): string {
        return '}';
    }
}
