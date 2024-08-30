import { MonacoEditorDomainAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-domain-action.model';
import { MonacoEditorWithActions } from 'app/shared/monaco-editor/model/actions/monaco-editor.util';

export class MonacoQuizExplanationAction extends MonacoEditorDomainAction {
    static readonly ID = 'monaco-quiz-explanation.action';
    static readonly IDENTIFIER = '[exp]';
    static readonly TEXT = 'Add an explanation here (only visible in feedback after quiz has ended)';

    constructor() {
        super(MonacoQuizExplanationAction.ID, 'artemisApp.multipleChoiceQuestion.editor.addExplanation');
    }

    run(editor: MonacoEditorWithActions): void {
        this.addTextWithDomainActionIdentifier(editor, MonacoQuizExplanationAction.TEXT, true);
    }

    getOpeningIdentifier(): string {
        return MonacoQuizExplanationAction.IDENTIFIER;
    }
}
