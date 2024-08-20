import * as monaco from 'monaco-editor';
import { MonacoEditorDomainAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-domain-action.model';

export class MonacoGradingUsageCountAction extends MonacoEditorDomainAction {
    static readonly ID = 'monaco-grading-usage-count.action';
    static readonly IDENTIFIER = '[maxCountInScore]';
    static readonly TEXT = '0';

    constructor() {
        super(MonacoGradingUsageCountAction.ID, 'artemisApp.assessmentInstructions.instructions.editor.addUsageCount', undefined, undefined, true);
    }

    run(editor: monaco.editor.ICodeEditor): void {
        this.addTextWithDomainActionIdentifier(editor, MonacoGradingUsageCountAction.TEXT, true, false);
    }

    getOpeningIdentifier(): string {
        return MonacoGradingUsageCountAction.IDENTIFIER;
    }
}
