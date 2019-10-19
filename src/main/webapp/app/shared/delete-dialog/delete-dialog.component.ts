import { Component, OnInit, Output, EventEmitter, Input } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { mapValues } from 'lodash';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { JhiAlertService } from 'ng-jhipster';

@Component({
    selector: 'jhi-delete-dialog',
    templateUrl: './delete-dialog.component.html',
})
export class DeleteDialogComponent implements OnInit {
    readonly actionTypes = ActionType;
    @Output() delete: EventEmitter<any>;
    confirmEntityName: string;
    entityTitle: string;
    deleteQuestion: string;
    deleteConfirmationText: string;
    additionalChecks?: { [key: string]: string };
    additionalChecksValues: { [key: string]: boolean } = {};
    actionType: ActionType;

    // used by *ngFor in the template
    objectKeys = Object.keys;

    constructor(public activeModal: NgbActiveModal, public jhiAlertService: JhiAlertService) {}

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
     * Closes the dialog with a 'confirm' message, so the user of the service can use this message to delete the entity
     */
    confirmDelete() {
        this.delete.emit(this.additionalChecksValues);
    }

    close() {
        this.activeModal.close();
    }
}
