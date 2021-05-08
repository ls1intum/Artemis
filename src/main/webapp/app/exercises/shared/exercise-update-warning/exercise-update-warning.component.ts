import { Component, OnInit, EventEmitter, Output } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-exercise-update-warning',
    templateUrl: './exercise-update-warning.component.html',
})
export class ExerciseUpdateWarningComponent implements OnInit {
    instructionDeleted = false;
    scoringChanged = false;

    @Output()
    confirmed = new EventEmitter<object>();

    constructor(public activeModal: NgbActiveModal) {}

    ngOnInit(): void {}

    /**
     * Closes the modal
     */
    clear(): void {
        this.activeModal.close();
    }

    /**
     * Confirm changes
     */
    confirmChange(): void {
        this.confirmed.emit();
        this.activeModal.close();
    }
}
