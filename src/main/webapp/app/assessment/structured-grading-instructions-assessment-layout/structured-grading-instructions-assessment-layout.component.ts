import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { GradingCriterion } from 'app/exercises/shared/structured-grading-criterion/grading-criterion.model';
import { Component, Input } from '@angular/core';

@Component({
    selector: 'jhi-structured-grading-instructions-assessment-layout',
    templateUrl: './structured-grading-instructions-assessment-layout.component.html',
})
export class StructuredGradingInstructionsAssessmentLayoutComponent {
    @Input() public criteria: GradingCriterion[];

    setTooltip(instr: GradingInstruction) {
        return 'Feedback: ' + instr.feedback;
    }
    setInstrColour(instr: GradingInstruction) {
        let colour;
        if (instr.credits === 0) {
            colour = '#fff2cc';
        } else if (instr.credits < 0) {
            colour = '#fbe5d6';
        } else {
            colour = '#e3f0da';
        }
        return colour;
    }
    setScore(nr: number) {
        return nr + 'P';
    }
    /**
     * Connects the SGI with the Feedback of a Submission Element in assessment detail
     * @param {Event} event - The drag event
     * @param {Object} instruction - The SGI element that should be connected with the feedback on drop
     * the corresponding drop method is in AssessmentDetailComponent
     */
    drag(event: any, instruction: GradingInstruction) {
        event.dataTransfer.setData('artemis/sgi', JSON.stringify(instruction));
    }
}
