import { MonacoEditorDomainAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-domain-action.model';
import * as monaco from 'monaco-editor';

export class MonacoGradingCreditsAction extends MonacoEditorDomainAction {
    static readonly ID = 'monaco-grading-credits.action';

    constructor() {
        super(MonacoGradingCreditsAction.ID, 'artemisApp.assessmentInstructions.instructions.editor.addCredits', undefined, undefined, true);
    }

    run(editor: monaco.editor.ICodeEditor): void {
        this.insertTextAtPosition(editor, this.getPosition(editor), `\t${this.getOpeningIdentifier()}\n`);
    }

    getOpeningIdentifier(): string {
        return '[credits]';
    }
}
