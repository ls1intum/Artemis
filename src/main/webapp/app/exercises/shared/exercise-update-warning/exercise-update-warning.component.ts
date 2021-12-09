import { Component, EventEmitter, Output } from '@angular/core';
import { faBan, faCheck, faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-exercise-update-warning',
    templateUrl: './exercise-update-warning.component.html',
    styleUrls: ['./exercise-update-warning.component.scss'],
})
export class ExerciseUpdateWarningComponent {
    instructionDeleted = false;
    creditChanged = false;
    deleteFeedback = false;
    usageCountChanged = false;

    @Output()
    confirmed = new EventEmitter<object>();

    @Output()
    reEvaluated = new EventEmitter<object>();

    canceled = new EventEmitter<void>();

    // Icons
    faBan = faBan;
    faCheck = faCheck;
    faExclamationTriangle = faExclamationTriangle;

    constructor(public activeModal: NgbActiveModal) {}

    /**
     * Closes the modal
     */
    clear(): void {
        this.canceled.emit();
        this.activeModal.close();
    }

    /**
     * Save changes without re-evaluation
     */
    saveExerciseWithoutReevaluation(): void {
        this.confirmed.emit();
        this.activeModal.close();
    }

    /**
     * Re-evaluate the exercise
     */
    reEvaluateExercise(): void {
        this.reEvaluated.emit();
        this.activeModal.close();
    }

    /**
     * Toggle the option to deleteFeedback
     */
    toggleDeleteFeedback() {
        this.deleteFeedback = !this.deleteFeedback;
    }
}
