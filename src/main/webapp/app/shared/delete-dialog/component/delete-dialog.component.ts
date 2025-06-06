import { Component, EventEmitter, OnDestroy, OnInit, Output, ViewChild, inject } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { mapValues } from 'lodash-es';
import { ActionType, EntitySummary } from 'app/shared/delete-dialog/delete-dialog.model';
import { Observable, Subscription } from 'rxjs';
import { AlertService } from 'app/shared/service/alert.service';
import { faBan, faCheck, faSpinner, faTimes, faTrash, faUndo } from '@fortawesome/free-solid-svg-icons';
import { ButtonType } from 'app/shared/components/buttons/button/button.component';
import { FormsModule, NgForm } from '@angular/forms';
import { TranslateDirective } from '../../language/translate.directive';
import { ConfirmEntityNameComponent } from '../../confirm-entity-name/confirm-entity-name.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgClass } from '@angular/common';
import { ArtemisTranslatePipe } from '../../pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-delete-dialog',
    templateUrl: './delete-dialog.component.html',
    imports: [FormsModule, TranslateDirective, ConfirmEntityNameComponent, FaIconComponent, NgClass, ArtemisTranslatePipe],
})
export class DeleteDialogComponent implements OnInit, OnDestroy {
    protected readonly faBan = faBan;
    protected readonly faSpinner = faSpinner;
    protected readonly faTimes = faTimes;
    protected readonly faTrash = faTrash;
    protected readonly faCheck = faCheck;
    protected readonly faUndo = faUndo;

    private activeModal = inject(NgbActiveModal);
    private alertService = inject(AlertService);

    readonly actionTypes = ActionType;
    private dialogErrorSubscription: Subscription;
    dialogError: Observable<string>;
    @Output() delete: EventEmitter<{ [key: string]: boolean }>;
    submitDisabled: boolean;
    confirmEntityName: string;
    entityTitle: string;
    buttonType: ButtonType;
    @ViewChild('deleteForm', { static: true }) deleteForm: NgForm;

    deleteQuestion: string;
    entitySummaryTitle?: string;
    entitySummary?: EntitySummary = {};
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

    warningTextColor: string;

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
}
