import { addTextAtCursor } from 'app/shared/util/markdown-util';
import { DomainTagCommand } from 'app/shared/markdown-editor/domainCommands/domainTag.command';
import { TranslateService } from '@ngx-translate/core';

export class IncorrectOptionCommand extends DomainTagCommand {
    public static readonly identifier = '[wrong]';
    buttonTranslationString = 'artemisApp.multipleChoiceQuestion.editor.addInCorrectAnswerOption';

    constructor(private translateService?: TranslateService) {
        super();
    }

    /**
     * @function execute
     * @desc Add a new incorrect answer option to the text editor at the location of the cursor
     */
    execute(): void {
        let text = '\n' + this.getOpeningIdentifier();
        if (this.translateService) {
            text += ' ' + this.translateService.instant('artemisApp.multipleChoiceQuestion.wrongAnswer');
        } else {
            text += ' Enter a wrong answer option here';
        }
        addTextAtCursor(text, this.aceEditor);
    }

    /**
     * @function getOpeningIdentifier
     * @desc identify the start of the correct option
     */
    getOpeningIdentifier(): string {
        return IncorrectOptionCommand.identifier;
    }

    /**
     * @function getClosingIdentifier
     * @desc identify the end of the correct option
     */
    getClosingIdentifier(): string {
        return '[/wrong]';
    }
}
