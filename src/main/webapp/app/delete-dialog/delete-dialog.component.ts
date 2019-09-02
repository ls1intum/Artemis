import { Component } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-delete-dialog',
    templateUrl: './delete-dialog.component.html',
})
export class DeleteDialogComponent {
    confirmExerciseName: string;
    entityTitle: string;
    deleteQuestion: string;
    deleteConfirmationText: string;

    constructor(public activeModal: NgbActiveModal) {}

    /**
     * Closes the dialog
     */
    clear() {
        this.activeModal.dismiss('cancel');
    }

    /**
     * Deletes specified entity and closes the dialog
     */
    confirmDelete() {
        this.activeModal.close();
    }
}
