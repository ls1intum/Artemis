import { TextEditorDomainAction } from 'app/editor/monaco-editor/model/actions/text-editor-domain-action.model';
import { TextEditor } from 'app/editor/monaco-editor/model/actions/adapter/text-editor.interface';
import { QUIZ_EXPLANATION_IDENTIFIER } from 'app/foundation/constants/quiz-markdown-identifiers.constants';

export class QuizExplanationAction extends TextEditorDomainAction {
    static readonly ID = 'quiz-explanation.action';
    static readonly IDENTIFIER = QUIZ_EXPLANATION_IDENTIFIER;
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
