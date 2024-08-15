import { MonacoEditorDomainAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-domain-action.model';
import * as monaco from 'monaco-editor';

export class MonacoGradingScaleAction extends MonacoEditorDomainAction {
    static readonly ID = 'monaco-grading-scale.action';

    constructor() {
        super(MonacoGradingScaleAction.ID, 'artemisApp.assessmentInstructions.instructions.editor.addScale', undefined, undefined, true);
    }

    run(editor: monaco.editor.ICodeEditor): void {
        this.insertTextAtPosition(editor, this.getPosition(editor), `\t${this.getOpeningIdentifier()}\n`);
    }

    getOpeningIdentifier(): string {
        return '[gradingScale]';
    }
}
