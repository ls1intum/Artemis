import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Feedback } from 'app/entities/feedback.model';
import { GradingCriterion } from 'app/exercises/shared/structured-grading-criterion/grading-criterion.model';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';

@Component({
    selector: 'jhi-textblock-feedback-dropdown',
    templateUrl: './textblock-feedback-dropdown.component.html',
    styleUrls: ['./textblock-feedback-dropdown.component.scss'],
})
export class TextblockFeedbackDropdownComponent {
    @Output() didChange = new EventEmitter();
    @Input() criterion: GradingCriterion;
    @Input() feedback: Feedback = new Feedback();

    updateAssessmentWithDropdown(instruction: GradingInstruction) {
        this.feedback.gradingInstruction = instruction;
        this.feedback.credits = instruction.credits;

        // Reset the feedback correction status upon setting grading instruction in order to hide it.
        this.feedback.correctionStatus = undefined;

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
