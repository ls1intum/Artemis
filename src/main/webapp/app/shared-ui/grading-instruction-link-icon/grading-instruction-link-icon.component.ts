import { Component, inject, input, output } from '@angular/core';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { faLink, faTrash } from '@fortawesome/free-solid-svg-icons';
import { ConfirmIconComponent } from 'app/shared-ui/confirm-icon/confirm-icon.component';

@Component({
    selector: 'jhi-grading-instruction-link-icon',
    templateUrl: './grading-instruction-link-icon.component.html',
    imports: [ConfirmIconComponent, ArtemisTranslatePipe],
})
export class GradingInstructionLinkIconComponent {
    private readonly artemisTranslatePipe = inject(ArtemisTranslatePipe);

    linkIcon = input(faLink);
    feedback = input.required<Feedback>();
    /** Fired after the grading instruction is cleared so parents can refresh usage counts (zoneless). */
    readonly linkRemoved = output<void>();

    confirmIcon = faTrash;

    /**
     * remove grading instruction on click
     */
    removeLink(): void {
        this.feedback().gradingInstruction = undefined;
        this.linkRemoved.emit();
    }

    /**
     * Set the tooltip of the link icon to be equal to the grading instruction description text
     * @param {GradingInstruction} instruction - the instruction object which is associated with feedback
     */
    setTooltip(instruction?: GradingInstruction) {
        return this.artemisTranslatePipe.transform('artemisApp.exercise.assessmentInstruction') + (instruction?.instructionDescription ?? '');
    }
}
