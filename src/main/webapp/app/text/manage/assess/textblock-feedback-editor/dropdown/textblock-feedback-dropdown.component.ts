import { Component, input, output } from '@angular/core';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';

@Component({
    selector: 'jhi-textblock-feedback-dropdown',
    templateUrl: './textblock-feedback-dropdown.component.html',
    styleUrls: ['./textblock-feedback-dropdown.component.scss'],
    imports: [HelpIconComponent],
})
export class TextblockFeedbackDropdownComponent {
    didChange = output();
    criterion = input.required<GradingCriterion>();
    feedback = input<Feedback>(new Feedback());

    updateAssessmentWithDropdown(instruction: GradingInstruction) {
        const feedbackValue = this.feedback();
        feedbackValue.gradingInstruction = instruction;
        feedbackValue.credits = instruction.credits;

        // Reset the feedback correction status upon setting grading instruction in order to hide it.
        feedbackValue.correctionStatus = undefined;

        this.didChange.emit();
    }

    /**
     * Get the color for grading instruction based on the credits of the instruction
     *  @param {GradingInstruction} instr - the instruction object we get its color based on its credits
     */
    getInstrColour(instr: GradingInstruction): string | undefined {
        if (instr.credits === 0) {
            return 'var(--sgi-assessment-layout-zero-background)';
        } else if (instr.credits < 0) {
            return 'var(--sgi-assessment-layout-negative-background)';
        } else if (instr.credits > 0) {
            return 'var(--sgi-assessment-layout-positive-background)';
        }
        return undefined;
    }
}
