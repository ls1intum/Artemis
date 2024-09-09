import { MonacoEditorDomainAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-domain-action.model';
import * as monaco from 'monaco-editor';

export class MonacoGradingScaleAction extends MonacoEditorDomainAction {
    static readonly ID = 'monaco-grading-scale.action';
    static readonly IDENTIFIER = '[gradingScale]';
    static readonly TEXT = 'Add instruction grading scale here (only visible for tutors)';

    constructor() {
        super(MonacoGradingScaleAction.ID, 'artemisApp.assessmentInstructions.instructions.editor.addScale', undefined, undefined, true);
    }

    run(editor: monaco.editor.ICodeEditor): void {
        this.addTextWithDomainActionIdentifier(editor, MonacoGradingScaleAction.TEXT, true, false);
    }

    getOpeningIdentifier(): string {
        return MonacoGradingScaleAction.IDENTIFIER;
    }
}
