import { TextEditorDomainAction } from 'app/shared/monaco-editor/model/actions/text-editor-domain-action.model';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';

export class GradingFeedbackAction extends TextEditorDomainAction {
    static readonly ID = 'grading-feedback.action';
    static readonly IDENTIFIER = '[feedback]';
    static readonly TEXT = 'Add feedback for students here (visible for students)';

    constructor() {
        super(GradingFeedbackAction.ID, 'artemisApp.assessmentInstructions.instructions.editor.addFeedback', undefined, undefined, true);
    }

    run(editor: TextEditor): void {
        this.addTextWithDomainActionIdentifier(editor, GradingFeedbackAction.TEXT, true, false);
    }

    getOpeningIdentifier(): string {
        return GradingFeedbackAction.IDENTIFIER;
    }
}
