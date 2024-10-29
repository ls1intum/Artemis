import { TextEditorDomainAction } from 'app/shared/monaco-editor/model/actions/text-editor-domain-action.model';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';

export class QuizHintAction extends TextEditorDomainAction {
    static readonly ID = 'quiz-hint.action';
    static readonly IDENTIFIER = '[hint]';
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
