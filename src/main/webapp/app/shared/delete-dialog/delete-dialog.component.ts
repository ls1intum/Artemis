import { Component, OnInit, Output, EventEmitter, OnDestroy } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { mapValues } from 'lodash';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { Observable, Subscription } from 'rxjs';
import { JhiAlertService } from 'ng-jhipster';

@Component({
    selector: 'jhi-delete-dialog',
    templateUrl: './delete-dialog.component.html',
})
export class DeleteDialogComponent implements OnInit, OnDestroy {
    readonly actionTypes = ActionType;
    private dialogErrorSubscription: Subscription;
    dialogError: Observable<string>;
    @Output() delete: EventEmitter<{ [key: string]: boolean }>;
    submitDisabled: boolean;
    confirmEntityName: string;
    entityTitle: string;
    deleteQuestion: string;
    deleteConfirmationText: string;
    additionalChecks?: { [key: string]: string };
    additionalChecksValues: { [key: string]: boolean } = {};
    actionType: ActionType;

    // used by *ngFor in the template
    objectKeys = Object.keys;

    constructor(private activeModal: NgbActiveModal, private jhiAlertService: JhiAlertService) {}

    ngOnInit(): void {
        this.dialogErrorSubscription = this.dialogError.subscribe((errorMessage: string) => {
            if (errorMessage === '') {
                this.clear();
            } else {
                this.submitDisabled = false;
                this.jhiAlertService.error(errorMessage);
            }
        });
        if (this.additionalChecks) {
            this.additionalChecksValues = mapValues(this.additionalChecks, () => false);
        }
    }

    ngOnDestroy(): void {
        if (this.dialogErrorSubscription) {
            this.dialogErrorSubscription.unsubscribe();
        }
    }

    /**
     * Closes the dialog
     */
    clear(): void {
        this.activeModal.dismiss();
    }

    /**
     * Emits delete event and passes additional checks from the dialog
     */
    confirmDelete(): void {
        this.submitDisabled = true;
        this.delete.emit(this.additionalChecksValues);
    }
}
