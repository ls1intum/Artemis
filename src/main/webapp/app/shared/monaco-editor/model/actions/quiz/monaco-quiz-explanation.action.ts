import { MonacoEditorDomainAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-domain-action.model';
import * as monaco from 'monaco-editor';

export class MonacoQuizExplanationAction extends MonacoEditorDomainAction {
    static readonly ID = 'monaco-quiz-explanation.action';
    static readonly IDENTIFIER = '[exp]';
    static readonly TEXT = 'Add an explanation here (only visible in feedback after quiz has ended)';

    constructor() {
        super(MonacoQuizExplanationAction.ID, 'artemisApp.multipleChoiceQuestion.editor.addExplanation');
    }

    run(editor: monaco.editor.ICodeEditor): void {
        this.addTextWithDomainActionIdentifier(editor, MonacoQuizExplanationAction.TEXT, true);
    }

    getOpeningIdentifier(): string {
        return MonacoQuizExplanationAction.IDENTIFIER;
    }
}
