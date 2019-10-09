import { Component } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-delete-dialog',
    templateUrl: './delete-dialog.component.html',
})
export class DeleteDialogComponent {
    confirmEntityName: string;
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
     * Closes the dialog with a 'confirm' message, so the user of the service can use this message to delete the entity
     */
    confirmDelete() {
        this.activeModal.close();
    }
}
