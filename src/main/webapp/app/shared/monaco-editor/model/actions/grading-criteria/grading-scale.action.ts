import { TextEditorDomainAction } from 'app/shared/monaco-editor/model/actions/text-editor-domain-action.model';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';

export class GradingScaleAction extends TextEditorDomainAction {
    static readonly ID = 'grading-scale.action';
    static readonly IDENTIFIER = '[gradingScale]';
    static readonly TEXT = 'Add instruction grading scale here (only visible for tutors)';

    constructor() {
        super(GradingScaleAction.ID, 'artemisApp.assessmentInstructions.instructions.editor.addScale', undefined, undefined, true);
    }

    run(editor: TextEditor): void {
        this.addTextWithDomainActionIdentifier(editor, GradingScaleAction.TEXT, true, false);
    }

    getOpeningIdentifier(): string {
        return GradingScaleAction.IDENTIFIER;
    }
}
