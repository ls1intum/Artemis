import { Component, Input } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-delete-dialog',
    templateUrl: './delete-dialog.component.html',
})
export class DeleteDialogComponent {
    entity: any;
    confirmExerciseName: string;
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
     * Deletes specified modeling exercise and closes the dialog
     * @param id
     */
    confirmDelete(id: number) {
        this.activeModal.close(id);
    }
}
