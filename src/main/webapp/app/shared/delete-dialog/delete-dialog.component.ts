import { Component, OnInit, Output, EventEmitter } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { mapValues } from 'lodash';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';

@Component({
    selector: 'jhi-delete-dialog',
    templateUrl: './delete-dialog.component.html',
})
export class DeleteDialogComponent implements OnInit {
    readonly actionTypes = ActionType;
    @Output() delete: EventEmitter<{ [key: string]: boolean }>;
    confirmEntityName: string;
    entityTitle: string;
    deleteQuestion: string;
    deleteConfirmationText: string;
    additionalChecks?: { [key: string]: string };
    additionalChecksValues: { [key: string]: boolean } = {};
    actionType: ActionType;

    // used by *ngFor in the template
    objectKeys = Object.keys;

    constructor(public activeModal: NgbActiveModal) {}

    ngOnInit(): void {
        if (this.additionalChecks) {
            this.additionalChecksValues = mapValues(this.additionalChecks, () => false);
        }
    }

    /**
     * Closes the dialog
     */
    clear() {
        this.activeModal.dismiss();
    }

    /**
     * Emits delete event and passes additional checks from the dialog
     */
    confirmDelete() {
        this.delete.emit(this.additionalChecksValues);
    }
}
