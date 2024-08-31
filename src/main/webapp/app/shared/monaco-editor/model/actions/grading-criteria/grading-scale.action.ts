import { TextEditorDomainAction } from 'app/shared/monaco-editor/model/actions/text-editor-domain-action.model';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';

export class MonacoGradingScaleAction extends TextEditorDomainAction {
    static readonly ID = 'monaco-grading-scale.action';
    static readonly IDENTIFIER = '[gradingScale]';
    static readonly TEXT = 'Add instruction grading scale here (only visible for tutors)';

    constructor() {
        super(MonacoGradingScaleAction.ID, 'artemisApp.assessmentInstructions.instructions.editor.addScale', undefined, undefined, true);
    }

    run(editor: TextEditor): void {
        this.addTextWithDomainActionIdentifier(editor, MonacoGradingScaleAction.TEXT, true, false);
    }

    getOpeningIdentifier(): string {
        return MonacoGradingScaleAction.IDENTIFIER;
    }
}
