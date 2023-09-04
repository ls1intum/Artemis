import { Component, EventEmitter, OnDestroy, OnInit, Output } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { mapValues } from 'lodash-es';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { Observable, Subscription } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { faBan, faCheck, faSpinner, faTimes } from '@fortawesome/free-solid-svg-icons';
import { ButtonType } from 'app/shared/components/button.component';

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
    buttonType: ButtonType;

    deleteQuestion: string;
    translateValues: { [key: string]: unknown } = {};
    deleteConfirmationText: string;
    requireConfirmationOnlyForAdditionalChecks: boolean;
    additionalChecks?: { [key: string]: string };
    additionalChecksValues: { [key: string]: boolean } = {};
    actionType: ActionType;
    // do not use faTimes icon if it's a confirmation but not a delete dialog
    useFaCheckIcon: boolean;

    // used by *ngFor in the template
    objectKeys = Object.keys;

    // Icons
    faBan = faBan;
    faSpinner = faSpinner;
    faTimes = faTimes;
    faCheck = faCheck;
    warningTextColor: string;

    constructor(
        private activeModal: NgbActiveModal,
        private alertService: AlertService,
    ) {}

    /**
     * Life cycle hook called by Angular to indicate that Angular is done creating the component
     */
    ngOnInit(): void {
        this.dialogErrorSubscription = this.dialogError.subscribe((errorMessage: string) => {
            if (errorMessage === '') {
                this.clear();
            } else {
                this.submitDisabled = false;
                this.alertService.error(errorMessage);
            }
        });
        if (this.additionalChecks) {
            this.additionalChecksValues = mapValues(this.additionalChecks, () => false);
        }
        this.useFaCheckIcon = this.buttonType !== ButtonType.ERROR;
        if (ButtonType.ERROR !== this.buttonType) {
            this.warningTextColor = 'text-default';
        } else {
            this.warningTextColor = 'text-danger';
        }
    }

    /**
     * Life cycle hook called by Angular for cleanup just before Angular destroys the component
     */
    ngOnDestroy(): void {
        if (this.dialogErrorSubscription) {
            this.dialogErrorSubscription.unsubscribe();
        }
    }

    /**
     * Closes the dialog
     */
    clear(): void {
        // intentionally use close instead of dismiss here, because dismiss leads to a non-traceable runtime error
        this.activeModal.close();
    }

    /**
     * Emits delete event and passes additional checks from the dialog
     */
    confirmDelete(): void {
        this.submitDisabled = true;
        this.delete.emit(this.additionalChecksValues);
    }

    /**
     * Check if at least one additionalCheck is selected
     */
    get isAnyAdditionalCheckSelected(): boolean {
        return Object.values(this.additionalChecksValues).some((check) => check);
    }

    /**
     * Check if all security checks are fulfilled
     * if deleteConfirmationText is passed the entityTitle and entered confirmation have to match
     * if requireConfirmationOnlyForAdditionalChecks is passed:
     *   if at least one additional check is selected the entityTitle and entered confirmation also have to match
     */
    get areSecurityChecksFulfilled(): boolean {
        return !(
            this.deleteConfirmationText &&
            this.confirmEntityName !== this.entityTitle &&
            (!this.requireConfirmationOnlyForAdditionalChecks || this.isAnyAdditionalCheckSelected)
        );
    }
}
