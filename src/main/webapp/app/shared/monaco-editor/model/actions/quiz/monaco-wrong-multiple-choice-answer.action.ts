import { MonacoEditorDomainAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-domain-action.model';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-adapter.model';

export class MonacoWrongMultipleChoiceAnswerAction extends MonacoEditorDomainAction {
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
