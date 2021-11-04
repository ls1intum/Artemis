import { addTextAtCursor } from 'app/shared/util/markdown-util';
import { DomainTagCommand } from 'app/shared/markdown-editor/domainCommands/domainTag.command';
import { TranslateService } from '@ngx-translate/core';

export class HintCommand extends DomainTagCommand {
    public static readonly identifier = '[hint]';

    buttonTranslationString = 'artemisApp.multipleChoiceQuestion.editor.addHint';

    constructor(private translateService: TranslateService) {
        super();
    }

    /**
     * @function execute
     * @desc Add a new hint to the answer option or question title in the editor at the location of the cursor
     */
    execute(): void {
        const text = '\n\t' + this.getOpeningIdentifier() + ' ' + this.translateService.instant('artemisApp.quizQuestion.hintText');
        addTextAtCursor(text, this.aceEditor);
    }

    /**
     * @function getOpeningIdentifier
     * @desc identify the start of the hint
     */
    getOpeningIdentifier(): string {
        return HintCommand.identifier;
    }

    /**
     * @function getClosingIdentifier
     * @desc identify the end of the hint
     */
    getClosingIdentifier(): string {
        return '[/hint]';
    }
}
