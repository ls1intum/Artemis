import * as monaco from 'monaco-editor';
import { MonacoEditorDomainAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-domain-action.model';

export class MonacoGradingFeedbackAction extends MonacoEditorDomainAction {
    static readonly ID = 'monaco-grading-feedback.action';

    constructor() {
        super(MonacoGradingFeedbackAction.ID, 'artemisApp.assessmentInstructions.instructions.editor.addFeedback', undefined, undefined, true);
    }

    run(editor: monaco.editor.ICodeEditor): void {
        this.insertTextAtPosition(editor, this.getPosition(editor), `\t${this.getOpeningIdentifier()}\n`);
    }

    getOpeningIdentifier(): string {
        return '[feedback]';
    }
}
