import { TextEditorDomainAction } from 'app/shared/monaco-editor/model/actions/text-editor-domain-action.model';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';

export class WrongMultipleChoiceAnswerAction extends TextEditorDomainAction {
    static readonly ID = 'incorrect-multiple-choice-answer.action';
    static readonly IDENTIFIER = '[wrong]';
    static readonly TEXT = 'Enter a wrong answer option here';

    constructor() {
        super(WrongMultipleChoiceAnswerAction.ID, 'artemisApp.multipleChoiceQuestion.editor.addInCorrectAnswerOption');
    }

    run(editor: TextEditor): void {
        this.addTextWithDomainActionIdentifier(editor, WrongMultipleChoiceAnswerAction.TEXT);
    }

    getOpeningIdentifier(): string {
        return WrongMultipleChoiceAnswerAction.IDENTIFIER;
    }
}
