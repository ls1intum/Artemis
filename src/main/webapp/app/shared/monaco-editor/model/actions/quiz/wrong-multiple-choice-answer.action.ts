import { TextEditorDomainAction } from 'app/shared/monaco-editor/model/actions/text-editor-domain-action.model';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';

export class MonacoWrongMultipleChoiceAnswerAction extends TextEditorDomainAction {
    static readonly ID = 'monaco-incorrect-multiple-choice-answer.action';
    static readonly IDENTIFIER = '[wrong]';
    static readonly TEXT = 'Enter a wrong answer option here';

    constructor() {
        super(MonacoWrongMultipleChoiceAnswerAction.ID, 'artemisApp.multipleChoiceQuestion.editor.addInCorrectAnswerOption');
    }

    run(editor: TextEditor): void {
        this.addTextWithDomainActionIdentifier(editor, MonacoWrongMultipleChoiceAnswerAction.TEXT);
    }

    getOpeningIdentifier(): string {
        return MonacoWrongMultipleChoiceAnswerAction.IDENTIFIER;
    }
}
