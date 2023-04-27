import { Component, Input } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseResetDialogComponent } from 'app/exercises/programming/manage/reset/programming-exercise-reset-dialog.component';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { faTimes } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

@Component({
    selector: 'jhi-programming-exercise-reset',
    templateUrl: './programming-exercise-reset-button.component.html',
})
export class ProgrammingExerciseResetButtonComponent {
    readonly ButtonType = ButtonType;
    readonly ButtonSize = ButtonSize;
    readonly FeatureToggle = FeatureToggle;

    @Input() programmingExercise: ProgrammingExercise;
    @Input() roundedCornersTop = true;
    @Input() roundedCornersBottom = true;
    @Input() fullWidth = false;

    // Icons
    faTimes = faTimes;

    constructor(private modalService: NgbModal) {}

    /**
     * Stops the propagation of the mouse event and updates the component instance
     * of the modalRef with this instance's values
     * @param {Event} event - Event
     */
    openResetDialog(event: Event) {
        event.stopPropagation();
        const modalRef = this.modalService.open(ProgrammingExerciseResetDialogComponent, { keyboard: true, size: 'lg' });
        modalRef.componentInstance.programmingExercise = this.programmingExercise;
    }
}
