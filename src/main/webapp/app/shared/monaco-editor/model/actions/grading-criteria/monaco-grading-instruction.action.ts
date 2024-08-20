import * as monaco from 'monaco-editor';
import { MonacoEditorDomainAction } from '../monaco-editor-domain-action.model';
import { MonacoGradingCreditsAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/monaco-grading-credits.action';
import { MonacoGradingScaleAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/monaco-grading-scale.action';
import { MonacoGradingDescriptionAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/monaco-grading-description.action';
import { MonacoGradingFeedbackAction } from './monaco-grading-feedback.action';
import { MonacoGradingUsageCountAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/monaco-grading-usage-count.action';

export class MonacoGradingInstructionAction extends MonacoEditorDomainAction {
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

    run(editor: monaco.editor.ICodeEditor): void {
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
