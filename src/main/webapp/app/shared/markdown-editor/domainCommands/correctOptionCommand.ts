import { addTextAtCursor } from 'app/shared/util/markdown-util';
import { DomainTagCommand } from 'app/shared/markdown-editor/domainCommands/domainTag.command';
import { TranslateService } from '@ngx-translate/core';

export class CorrectOptionCommand extends DomainTagCommand {
    public static readonly identifier = '[correct]';
    buttonTranslationString = 'artemisApp.multipleChoiceQuestion.editor.addCorrectAnswerOption';

    constructor(private translateService: TranslateService) {
        super();
    }

    /**
     * @function execute
     * @desc Add a new correct answer option to the text editor at the location of the cursor
     */
    execute(): void {
        const text = '\n' + this.getOpeningIdentifier() + ' ' + this.translateService.instant('artemisApp.multipleChoiceQuestion.correctAnswer');
        addTextAtCursor(text, this.aceEditor);
    }

    /**
     * @function getOpeningIdentifier
     * @desc identify the start of the correct option
     */
    getOpeningIdentifier(): string {
        return CorrectOptionCommand.identifier;
    }

    /**
     * @function getClosingIdentifier
     * @desc identify the end of the correct option
     */
    getClosingIdentifier(): string {
        return '[/correct]';
    }
}
