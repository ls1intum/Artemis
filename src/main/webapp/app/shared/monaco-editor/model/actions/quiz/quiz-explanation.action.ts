import { TextEditorDomainAction } from 'app/shared/monaco-editor/model/actions/text-editor-domain-action.model';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';

export class QuizExplanationAction extends TextEditorDomainAction {
    static readonly ID = 'quiz-explanation.action';
    static readonly IDENTIFIER = '[exp]';
    static readonly TEXT = 'Add an explanation here (only visible in feedback after quiz has ended)';

    constructor() {
        super(QuizExplanationAction.ID, 'artemisApp.multipleChoiceQuestion.editor.addExplanation');
    }

    run(editor: TextEditor): void {
        this.addTextWithDomainActionIdentifier(editor, QuizExplanationAction.TEXT, true);
    }

    getOpeningIdentifier(): string {
        return QuizExplanationAction.IDENTIFIER;
    }
}
