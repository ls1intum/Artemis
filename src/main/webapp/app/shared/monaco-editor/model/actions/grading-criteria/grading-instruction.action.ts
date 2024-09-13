import { TextEditorDomainAction } from '../text-editor-domain-action.model';
import { GradingCreditsAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/grading-credits.action';
import { GradingScaleAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/grading-scale.action';
import { GradingDescriptionAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/grading-description.action';
import { GradingFeedbackAction } from './grading-feedback.action';
import { GradingUsageCountAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/grading-usage-count.action';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';

export class GradingInstructionAction extends TextEditorDomainAction {
    static readonly ID = 'grading-instruction.action';
    static readonly IDENTIFIER = '[instruction]';

    constructor(
        private readonly creditsAction: GradingCreditsAction,
        private readonly scaleAction: GradingScaleAction,
        private readonly descriptionAction: GradingDescriptionAction,
        private readonly feedbackAction: GradingFeedbackAction,
        private readonly usageCountAction: GradingUsageCountAction,
    ) {
        super(GradingInstructionAction.ID, 'artemisApp.assessmentInstructions.instructions.editor.addInstruction');
    }

    run(editor: TextEditor): void {
        this.addTextWithDomainActionIdentifier(editor, '', false, false);
        this.creditsAction.executeInCurrentEditor();
        this.scaleAction.executeInCurrentEditor();
        this.descriptionAction.executeInCurrentEditor();
        this.feedbackAction.executeInCurrentEditor();
        this.usageCountAction.executeInCurrentEditor();
    }

    getOpeningIdentifier(): string {
        return GradingInstructionAction.IDENTIFIER;
    }
}
