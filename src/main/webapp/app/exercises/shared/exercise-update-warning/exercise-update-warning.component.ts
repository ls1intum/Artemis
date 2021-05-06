import { Component, OnInit, EventEmitter, Output } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-exercise-update-warning',
    templateUrl: './exercise-update-warning.component.html',
})
export class ExerciseUpdateWarningComponent implements OnInit {
    isSaving: boolean;
    gradingCriteriaDeleted = false;
    instructionDeleted = false;
    scoringChanged = false;

    @Output()
    confirmed = new EventEmitter<object>();

    constructor(public activeModal: NgbActiveModal) {}

    /**
     * Reset saving status, load the quiz by id and back it up.
     */
    ngOnInit(): void {
        this.isSaving = false;
    }

    /**
     * Closes the modal
     */
    clear(): void {
        this.activeModal.dismiss('cancel');
    }

    /**
     * Confirm changes
     */
    confirmChange(): void {
        this.isSaving = true;
        this.confirmed.emit();
        this.activeModal.close('test');
    }
}
