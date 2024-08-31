import { MonacoEditorDomainAction } from '../monaco-editor-domain-action.model';
import { MonacoGradingInstructionAction } from './monaco-grading-instruction.action';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';

export class MonacoGradingCriterionAction extends MonacoEditorDomainAction {
    static readonly ID = 'monaco-grading-criterion.action';
    static readonly IDENTIFIER = '[criterion]';
    static readonly TEXT = 'Add criterion title (only visible to tutors)';

    constructor(private readonly gradingInstructionAction: MonacoGradingInstructionAction) {
        super(MonacoGradingCriterionAction.ID, 'artemisApp.assessmentInstructions.instructions.editor.addCriterion');
    }

    run(editor: TextEditor): void {
        this.addTextWithDomainActionIdentifier(editor, MonacoGradingCriterionAction.TEXT, false, false);
        this.gradingInstructionAction.executeInCurrentEditor();
    }

    getOpeningIdentifier(): string {
        return MonacoGradingCriterionAction.IDENTIFIER;
    }
}
