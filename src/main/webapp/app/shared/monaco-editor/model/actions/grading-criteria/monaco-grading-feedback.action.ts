import * as monaco from 'monaco-editor';
import { MonacoEditorDomainAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-domain-action.model';

export class MonacoGradingFeedbackAction extends MonacoEditorDomainAction {
    static readonly ID = 'monaco-grading-feedback.action';
    static readonly IDENTIFIER = '[feedback]';
    static readonly TEXT = 'Add feedback for students here (visible for students)';

    constructor() {
        super(MonacoGradingFeedbackAction.ID, 'artemisApp.assessmentInstructions.instructions.editor.addFeedback', undefined, undefined, true);
    }

    run(editor: monaco.editor.ICodeEditor): void {
        this.addTextWithDomainActionIdentifier(editor, MonacoGradingFeedbackAction.TEXT, true, false);
    }

    getOpeningIdentifier(): string {
        return MonacoGradingFeedbackAction.IDENTIFIER;
    }
}
