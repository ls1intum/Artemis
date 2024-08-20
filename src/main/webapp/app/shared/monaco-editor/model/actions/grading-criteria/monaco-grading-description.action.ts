import * as monaco from 'monaco-editor';
import { MonacoEditorDomainAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-domain-action.model';

export class MonacoGradingDescriptionAction extends MonacoEditorDomainAction {
    static readonly ID = 'monaco-grading-description.action';
    static readonly IDENTIFIER = '[description]';
    static readonly TEXT = 'Add grading instruction here (only visible for tutors)';

    constructor() {
        super(MonacoGradingDescriptionAction.ID, 'artemisApp.assessmentInstructions.instructions.editor.addDescription', undefined, undefined, true);
    }

    run(editor: monaco.editor.ICodeEditor): void {
        this.addTextWithDomainActionIdentifier(editor, MonacoGradingDescriptionAction.TEXT, true, false);
    }

    getOpeningIdentifier(): string {
        return MonacoGradingDescriptionAction.IDENTIFIER;
    }
}
