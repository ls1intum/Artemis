import { Component, Input, OnInit } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { Feedback } from 'app/entities/feedback.model';

@Component({
    selector: 'jhi-structured-grading-instruction-assessment-link',
    templateUrl: './structured-grading-instruction-assessment-link.component.html',
})
export class StructuredGradingInstructionAssessmentLinkComponent implements OnInit {
    @Input() linkIcon = <IconProp>'link';
    @Input() assessment: Feedback;
    instruction: GradingInstruction | undefined;
    confirmIcon = <IconProp>'trash';
    showConfirm = false;

    ngOnInit(): void {
        this.instruction = this.assessment.gradingInstruction;
    }

    /**
     * remove grading instruction on click
     */
    removeLink(): void {
        this.toggle();
        this.assessment.gradingInstruction = undefined;
    }

    /**
     * Set the tooltip of the link icon to be equal to the grading instruction description text
     * @param {GradingInstruction} instruction - the instruction object which is associated with feedback
     */
    setTooltip(instruction: GradingInstruction) {
        return 'Grading Instruction: ' + instruction.instructionDescription;
    }

    /**
     * toggle showConfirm
     */
    toggle(): void {
        this.showConfirm = !this.showConfirm;
    }
}
