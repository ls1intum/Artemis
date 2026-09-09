import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { faLink, faTrash } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { NgClass } from '@angular/common';

@Component({
    selector: 'jhi-grading-instruction-link-icon',
    templateUrl: './grading-instruction-link-icon.component.html',
    imports: [FaIconComponent, NgbTooltip, NgClass, ArtemisTranslatePipe],
})
export class GradingInstructionLinkIconComponent implements OnInit {
    private artemisTranslatePipe = inject(ArtemisTranslatePipe);

    linkIcon = input(faLink);
    feedback = input.required<Feedback>();
    /** Fired after the grading instruction is cleared so parents can refresh usage counts (zoneless). */
    readonly linkRemoved = output<void>();

    instruction = signal<GradingInstruction | undefined>(undefined);
    confirmIcon = faTrash;
    readonly showConfirm = signal(false);

    ngOnInit(): void {
        this.instruction.set(this.feedback().gradingInstruction);
    }

    /**
     * remove grading instruction on click
     */
    removeLink(): void {
        this.toggle();
        this.feedback().gradingInstruction = undefined;
        this.instruction.set(undefined);
        this.linkRemoved.emit();
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
        this.showConfirm.update((value) => !value);
    }
}
