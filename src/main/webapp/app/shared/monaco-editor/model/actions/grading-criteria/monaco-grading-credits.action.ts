import { MonacoEditorDomainAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-domain-action.model';
import { MonacoEditorWithActions } from 'app/shared/monaco-editor/model/actions/monaco-editor.util';

export class MonacoGradingCreditsAction extends MonacoEditorDomainAction {
    static readonly ID = 'monaco-grading-credits.action';
    static readonly IDENTIFIER = '[credits]';
    static readonly TEXT = '0';

    constructor() {
        super(MonacoGradingCreditsAction.ID, 'artemisApp.assessmentInstructions.instructions.editor.addCredits', undefined, undefined, true);
    }

    run(editor: MonacoEditorWithActions): void {
        this.addTextWithDomainActionIdentifier(editor, MonacoGradingCreditsAction.TEXT, true, false);
    }

    getOpeningIdentifier(): string {
        return MonacoGradingCreditsAction.IDENTIFIER;
    }
}
