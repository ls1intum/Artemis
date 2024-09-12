import { TextEditorDomainAction } from 'app/shared/monaco-editor/model/actions/text-editor-domain-action.model';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';

export class GradingUsageCountAction extends TextEditorDomainAction {
    static readonly ID = 'grading-usage-count.action';
    static readonly IDENTIFIER = '[maxCountInScore]';
    static readonly TEXT = '0';

    constructor() {
        super(GradingUsageCountAction.ID, 'artemisApp.assessmentInstructions.instructions.editor.addUsageCount', undefined, undefined, true);
    }

    run(editor: TextEditor): void {
        this.addTextWithDomainActionIdentifier(editor, GradingUsageCountAction.TEXT, true, false);
    }

    getOpeningIdentifier(): string {
        return GradingUsageCountAction.IDENTIFIER;
    }
}
