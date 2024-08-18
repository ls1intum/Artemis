import { MonacoEditorDomainAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-domain-action.model';
import * as monaco from 'monaco-editor';

export class MonacoGradingCreditsAction extends MonacoEditorDomainAction {
    static readonly ID = 'monaco-grading-credits.action';
    static readonly IDENTIFIER = '[credits]';
    static readonly TEXT = '0';

    constructor() {
        super(MonacoGradingCreditsAction.ID, 'artemisApp.assessmentInstructions.instructions.editor.addCredits', undefined, undefined, true);
    }

    run(editor: monaco.editor.ICodeEditor): void {
        this.insertTextAtPosition(editor, this.getPosition(editor), `\t${this.getOpeningIdentifier()} ${MonacoGradingCreditsAction.TEXT}\n`);
    }

    getOpeningIdentifier(): string {
        return MonacoGradingCreditsAction.IDENTIFIER;
    }
}
