import { TextEditorDomainAction } from '../text-editor-domain-action.model';
import { GradingInstructionAction } from './grading-instruction.action';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';

export class GradingCriterionAction extends TextEditorDomainAction {
    static readonly ID = 'grading-criterion.action';
    static readonly IDENTIFIER = '[criterion]';
    static readonly TEXT = 'Add criterion title (only visible to tutors)';

    constructor(private readonly gradingInstructionAction: GradingInstructionAction) {
        super(GradingCriterionAction.ID, 'artemisApp.assessmentInstructions.instructions.editor.addCriterion');
    }

    run(editor: TextEditor): void {
        this.addTextWithDomainActionIdentifier(editor, GradingCriterionAction.TEXT, false, false);
        this.gradingInstructionAction.executeInCurrentEditor();
    }

    getOpeningIdentifier(): string {
        return GradingCriterionAction.IDENTIFIER;
    }
}
