import { addTextAtCursor } from 'app/shared/util/markdown-util';
import { DomainTagCommand } from 'app/shared/markdown-editor/domainCommands/domainTag.command';
import { TranslateService } from '@ngx-translate/core';

export class ExplanationCommand extends DomainTagCommand {
    public static readonly identifier = '[exp]';
    public static readonly text = ' Add an explanation here (only visible in feedback after quiz has ended)';

    buttonTranslationString = 'artemisApp.multipleChoiceQuestion.editor.addExplanation';

    constructor(private translateService?: TranslateService) {
        super();
    }
    /**
     * @function execute
     * @desc Add a new explanation to answer option or question title in the text editor at the location of the cursor
     */
    execute(): void {
        let text = '\n\t' + this.getOpeningIdentifier();
        if (this.translateService) {
            text += ' ' + this.translateService.instant('artemisApp.quizQuestion.explanationText');
        } else {
            text += ExplanationCommand.text;
        }
        addTextAtCursor(text, this.aceEditor);
    }

    /**
     * @function getOpeningIdentifier
     * @desc identify the start of the explanation
     */
    getOpeningIdentifier(): string {
        return ExplanationCommand.identifier;
    }

    /**
     * @function getClosingIdentifier
     * @desc identify the end of the explanation
     */
    getClosingIdentifier(): string {
        return '[/exp]';
    }
}
