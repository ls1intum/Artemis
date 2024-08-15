import * as monaco from 'monaco-editor';
import { MonacoEditorDomainAction } from '../monaco-editor-domain-action.model';
import { MonacoGradingInstructionAction } from './monaco-grading-instruction.action';

export class MonacoGradingCriterionAction extends MonacoEditorDomainAction {
    static readonly ID = 'monaco-grading-criterion.action';
    static readonly IDENTIFIER = '[criterion]';
    static readonly TEXT = ' Add criterion title (only visible to tutors)';

    constructor(private readonly gradingInstructionAction: MonacoGradingInstructionAction) {
        super(MonacoGradingCriterionAction.ID, 'artemisApp.assessmentInstructions.instructions.editor.addCriterion');
    }

    run(editor: monaco.editor.ICodeEditor): void {
        this.insertTextAtPosition(editor, this.getPosition(editor), this.getOpeningIdentifier() + MonacoGradingCriterionAction.TEXT + '\n');
        this.gradingInstructionAction.executeInCurrentEditor();
        editor.focus();
    }

    getOpeningIdentifier(): string {
        return MonacoGradingCriterionAction.IDENTIFIER;
    }
}
