import { TextEditorDomainAction } from '../text-editor-domain-action.model';
import { MonacoGradingCreditsAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/grading-credits.action';
import { MonacoGradingScaleAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/grading-scale.action';
import { MonacoGradingDescriptionAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/grading-description.action';
import { MonacoGradingFeedbackAction } from './grading-feedback.action';
import { MonacoGradingUsageCountAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/grading-usage-count.action';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';

export class MonacoGradingInstructionAction extends TextEditorDomainAction {
    static readonly ID = 'monaco-grading-instruction.action';
    static readonly IDENTIFIER = '[instruction]';

    constructor(
        private readonly creditsAction: MonacoGradingCreditsAction,
        private readonly scaleAction: MonacoGradingScaleAction,
        private readonly descriptionAction: MonacoGradingDescriptionAction,
        private readonly feedbackAction: MonacoGradingFeedbackAction,
        private readonly usageCountAction: MonacoGradingUsageCountAction,
    ) {
        super(MonacoGradingInstructionAction.ID, 'artemisApp.assessmentInstructions.instructions.editor.addInstruction');
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
        return MonacoGradingInstructionAction.IDENTIFIER;
    }
}
