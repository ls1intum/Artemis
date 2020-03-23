import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { GradingCriterion } from 'app/exercises/shared/structured-grading-criterion/grading-criterion.model';
import { Component, Input } from '@angular/core';

@Component({
    selector: 'jhi-structured-grading-instructions-assessment-layout',
    templateUrl: './structured-grading-instructions-assessment-layout.component.html',
})
export class StructuredGradingInstructionsAssessmentLayoutComponent {
    @Input() public criteria: GradingCriterion[];

    drag(ev: any, instruction: GradingInstruction) {
        ev.dataTransfer.setData('text', JSON.stringify(instruction));
    }

    setTooltip(instr: GradingInstruction) {
        return 'Feedback: ' + instr.feedback;
    }

    setInstrColour(instr: GradingInstruction) {
        let colour = '#e3f0da';
        if (instr.credits === 0) {
            colour = '#fff2cc';
        } else if (instr.credits < 0) {
            colour = '#fbe5d6';
        } else {
            colour = '#e3f0da';
        }
        return colour;
    }
}
