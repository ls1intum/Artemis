import { TextEditorDomainAction } from 'app/editor/monaco-editor/model/actions/text-editor-domain-action.model';
import { TextEditor } from 'app/editor/monaco-editor/model/actions/adapter/text-editor.interface';
import { QUIZ_HINT_IDENTIFIER } from 'app/foundation/constants/quiz-markdown-identifiers.constants';

export class QuizHintAction extends TextEditorDomainAction {
    static readonly ID = 'quiz-hint.action';
    static readonly IDENTIFIER = QUIZ_HINT_IDENTIFIER;
    static readonly TEXT = 'Add a hint here (visible during the quiz via ?-Button)';

    constructor() {
        super(QuizHintAction.ID, 'artemisApp.multipleChoiceQuestion.editor.addHint');
    }

    run(editor: TextEditor): void {
        this.addTextWithDomainActionIdentifier(editor, QuizHintAction.TEXT, true);
    }

    getOpeningIdentifier(): string {
        return QuizHintAction.IDENTIFIER;
    }
}
