import { DomainMultiOptionListCommand } from 'app/shared/markdown-editor/domainCommands/domain-multi-option-list.command';

export class TaskHintCommand extends DomainMultiOptionListCommand {
    buttonTranslationString = 'artemisApp.programmingExercise.problemStatement.exerciseHintCommand';

    protected getValueMeta(): string {
        return 'exerciseHint';
    }

    getOpeningIdentifier(): string {
        return '{';
    }

    getClosingIdentifier(): string {
        return '}';
    }
}
