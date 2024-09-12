import { TextEditorDomainAction } from 'app/shared/monaco-editor/model/actions/text-editor-domain-action.model';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';

export class GradingDescriptionAction extends TextEditorDomainAction {
    static readonly ID = 'grading-description.action';
    static readonly IDENTIFIER = '[description]';
    static readonly TEXT = 'Add grading instruction here (only visible for tutors)';

    constructor() {
        super(GradingDescriptionAction.ID, 'artemisApp.assessmentInstructions.instructions.editor.addDescription', undefined, undefined, true);
    }

    run(editor: TextEditor): void {
        this.addTextWithDomainActionIdentifier(editor, GradingDescriptionAction.TEXT, true, false);
    }

    getOpeningIdentifier(): string {
        return GradingDescriptionAction.IDENTIFIER;
    }
}
