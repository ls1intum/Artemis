import { MonacoEditorDomainAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-domain-action.model';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-adapter.model';

export class MonacoGradingUsageCountAction extends MonacoEditorDomainAction {
    static readonly ID = 'monaco-grading-usage-count.action';
    static readonly IDENTIFIER = '[maxCountInScore]';
    static readonly TEXT = '0';

    constructor() {
        super(MonacoGradingUsageCountAction.ID, 'artemisApp.assessmentInstructions.instructions.editor.addUsageCount', undefined, undefined, true);
    }

    run(editor: TextEditor): void {
        this.addTextWithDomainActionIdentifier(editor, MonacoGradingUsageCountAction.TEXT, true, false);
    }

    getOpeningIdentifier(): string {
        return MonacoGradingUsageCountAction.IDENTIFIER;
    }
}
