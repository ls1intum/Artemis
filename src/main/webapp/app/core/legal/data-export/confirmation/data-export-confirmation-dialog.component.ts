import { Component, EventEmitter, OnDestroy, OnInit, Output, ViewChild, inject } from '@angular/core';
import { Observable, Subscription } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { FormsModule, NgForm } from '@angular/forms';
import { faBan, faCheck, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TypeAheadUserSearchFieldModule } from 'app/shared/type-ahead-search-field/type-ahead-user-search-field.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-data-export-confirmation-dialog',
    templateUrl: './data-export-confirmation-dialog.component.html',
    standalone: true,
    imports: [FormsModule, TranslateDirective, TypeAheadUserSearchFieldModule, ArtemisSharedModule, FaIconComponent],
})
export class DataExportConfirmationDialogComponent implements OnInit, OnDestroy {
    private activeModal = inject(NgbActiveModal);
    private alertService = inject(AlertService);

    private dialogErrorSubscription: Subscription;
    dialogError: Observable<string>;

    @Output() dataExportRequest: EventEmitter<void>;
    @Output() dataExportRequestForAnotherUser: EventEmitter<string>;
    @ViewChild('dataExportConfirmationForm', { static: true }) dataExportConfirmationForm: NgForm;

    submitDisabled: boolean;
    enteredLogin: string;
    expectedLogin: string;
    adminDialog = false;
    requestForAnotherUser = false;
    expectedLoginOfOtherUser: string;
    deleteQuestion: string;
    confirmationTextHint: string;
    ownLogin: string;

    // Icons
    faBan = faBan;
    faSpinner = faSpinner;
    faCheck = faCheck;

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
        this.confirmationTextHint = 'artemisApp.dataExport.typeLoginToConfirm';
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
     * Emits the data export request event
     */
    confirmDataExportRequest(): void {
        this.submitDisabled = true;
        // we need to emit the login if it is a request by an admin for another user, so we can make the request for the data export using the login
        if (this.requestForAnotherUser) {
            this.dataExportRequestForAnotherUser.emit(this.expectedLogin);
        } else {
            this.dataExportRequest.emit();
        }
    }

    onRequestDataExportForOtherUserChanged(event: any) {
        if (event.target.checked) {
            this.ownLogin = this.expectedLogin;
            this.expectedLogin = this.expectedLoginOfOtherUser ?? '';
            this.confirmationTextHint = 'artemisApp.dataExport.typeUserLoginToConfirm';
            this.enteredLogin = '';
        } else {
            this.enteredLogin = '';
            this.expectedLogin = this.ownLogin;
            this.confirmationTextHint = 'artemisApp.dataExport.typeLoginToConfirm';
            this.expectedLoginOfOtherUser = '';
        }
    }
}
