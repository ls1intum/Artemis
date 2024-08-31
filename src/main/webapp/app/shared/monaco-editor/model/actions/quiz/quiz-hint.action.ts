import { TextEditorDomainAction } from 'app/shared/monaco-editor/model/actions/text-editor-domain-action.model';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';

export class MonacoQuizHintAction extends TextEditorDomainAction {
    static readonly ID = 'monaco-quiz-hint.action';
    static readonly IDENTIFIER = '[hint]';
    static readonly TEXT = 'Add a hint here (visible during the quiz via ?-Button)';

    constructor() {
        super(MonacoQuizHintAction.ID, 'artemisApp.multipleChoiceQuestion.editor.addHint');
    }

    run(editor: TextEditor): void {
        this.addTextWithDomainActionIdentifier(editor, MonacoQuizHintAction.TEXT, true);
    }

    getOpeningIdentifier(): string {
        return MonacoQuizHintAction.IDENTIFIER;
    }
}
