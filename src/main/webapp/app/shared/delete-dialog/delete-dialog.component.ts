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
    checkboxText?: string;
    additionalCheckboxText?: string;
    checkboxValue: boolean;
    additionalCheckboxValue: boolean;

    constructor(public activeModal: NgbActiveModal) {
        this.checkboxValue = false;
        this.additionalCheckboxValue = false;
    }

    /**
     * Closes the dialog
     */
    clear() {
        this.activeModal.dismiss();
    }

    /**
     * Closes the dialog with a 'confirm' message, so the user of the service can use this message to delete the entity
     */
    confirmDelete() {
        this.activeModal.close({ checkboxValue: this.checkboxValue, additionalCheckboxValue: this.additionalCheckboxValue });
    }
}
