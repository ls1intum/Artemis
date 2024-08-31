import { TextEditorDomainAction } from 'app/shared/monaco-editor/model/actions/text-editor-domain-action.model';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';

export class MonacoGradingUsageCountAction extends TextEditorDomainAction {
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
