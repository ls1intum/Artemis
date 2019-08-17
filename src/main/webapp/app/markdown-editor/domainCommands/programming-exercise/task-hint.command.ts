import { AceEditorComponent } from 'ng2-ace-editor';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { DomainTagCommand } from 'app/markdown-editor/domainCommands/domainTag.command';
import { escapeStringForUseInRegex, getStringSegmentPositions } from 'app/utils/global.utils';
import { DomainMultiOptionListCommand } from 'app/markdown-editor/domainCommands/domain-multi-option-list.command';

export class TaskHintCommand extends DomainMultiOptionListCommand {
    buttonTranslationString = 'artemisApp.programmingExercise.problemStatement.exerciseHintCommand';

    /**
     * @function getOpeningIdentifier
     * @desc identify the start of the task
     */
    getOpeningIdentifier(): string {
        return '{';
    }

    /**
     * @function getClosingIdentifier
     * @desc identify the end of the explanation
     */
    getClosingIdentifier(): string {
        return '}';
    }
}
