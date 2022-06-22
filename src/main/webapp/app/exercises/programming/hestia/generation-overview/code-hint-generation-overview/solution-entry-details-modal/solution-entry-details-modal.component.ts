import { Component } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseSolutionEntry } from 'app/entities/hestia/programming-exercise-solution-entry.model';

@Component({
    selector: 'jhi-solution-entry-details-modal',
    templateUrl: './solution-entry-details-modal.component.html',
})
export class SolutionEntryDetailsModalComponent {
    solutionEntry: ProgrammingExerciseSolutionEntry;
    isEditable: boolean;

    constructor(private activeModal: NgbActiveModal) {}

    clear() {
        this.activeModal.dismiss();
    }
}
