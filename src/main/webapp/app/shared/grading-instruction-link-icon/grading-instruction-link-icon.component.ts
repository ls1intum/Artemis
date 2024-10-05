import { Component, Input, OnInit, inject } from '@angular/core';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { Feedback } from 'app/entities/feedback.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { faLink, faTrash } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-grading-instruction-link-icon',
    templateUrl: './grading-instruction-link-icon.component.html',
})
export class GradingInstructionLinkIconComponent implements OnInit {
    private artemisTranslatePipe = inject(ArtemisTranslatePipe);

    @Input() linkIcon = faLink;
    @Input() feedback: Feedback;
    instruction: GradingInstruction | undefined;
    confirmIcon = faTrash;
    showConfirm = false;

    ngOnInit(): void {
        this.instruction = this.feedback.gradingInstruction;
    }

    /**
     * remove grading instruction on click
     */
    removeLink(): void {
        this.toggle();
        this.feedback.gradingInstruction = undefined;
    }

    /**
     * Set the tooltip of the link icon to be equal to the grading instruction description text
     * @param {GradingInstruction} instruction - the instruction object which is associated with feedback
     */
    setTooltip(instruction: GradingInstruction) {
        return this.artemisTranslatePipe.transform('artemisApp.exercise.assessmentInstruction') + instruction.instructionDescription;
    }

    /**
     * toggle showConfirm
     */
    toggle(): void {
        this.showConfirm = !this.showConfirm;
    }
}
