import { Component, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { mapValues, values } from 'lodash';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { Observable } from 'rxjs';
import { JhiAlertService } from 'ng-jhipster';
import { HttpResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-delete-dialog',
    templateUrl: './delete-dialog.component.html',
})
export class DeleteDialogComponent implements OnInit {
    readonly actionTypes = ActionType;

    entityParameter: any;
    delete: (entityParameter: any, ...params: any[]) => Observable<HttpResponse<void>>;
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
        this.delete(this.entityParameter, ...values(this.additionalChecksValues)).subscribe(
            () => this.activeModal.close(this.additionalChecksValues),
            error => this.jhiAlertService.error(error.message),
        );
    }
}
