import { Component, Input, OnInit } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { Feedback } from 'app/entities/feedback.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-grading-instruction-link-icon',
    templateUrl: './grading-instruction-link-icon.component.html',
})
export class GradingInstructionLinkIconComponent implements OnInit {
    @Input() linkIcon = <IconProp>'link';
    @Input() assessment: Feedback;
    instruction: GradingInstruction | undefined;
    confirmIcon = <IconProp>'trash';
    showConfirm = false;

    constructor(private artemisTranslatePipe: ArtemisTranslatePipe) {}

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
        return this.artemisTranslatePipe.transform('artemisApp.exercise.gradingInstruction') + instruction.instructionDescription;
    }

    /**
     * toggle showConfirm
     */
    toggle(): void {
        this.showConfirm = !this.showConfirm;
    }
}
