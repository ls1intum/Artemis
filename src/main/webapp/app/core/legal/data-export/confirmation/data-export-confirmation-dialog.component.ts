import { Component, DestroyRef, OnInit, OutputEmitterRef, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable } from 'rxjs';
import { AlertService } from 'app/shared/service/alert.service';
import { FormsModule, NgForm } from '@angular/forms';
import { faBan, faCheck, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ConfirmEntityNameComponent } from 'app/shared/confirm-entity-name/confirm-entity-name.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TypeAheadUserSearchFieldComponent } from 'app/core/legal/data-export/type-ahead-search-field/type-ahead-user-search-field.component';

@Component({
    selector: 'jhi-data-export-confirmation-dialog',
    templateUrl: './data-export-confirmation-dialog.component.html',
    imports: [FormsModule, TranslateDirective, TypeAheadUserSearchFieldComponent, ConfirmEntityNameComponent, FaIconComponent],
})
export class DataExportConfirmationDialogComponent implements OnInit {
    private readonly activeModal = inject(NgbActiveModal);
    private readonly alertService = inject(AlertService);
    private readonly destroyRef = inject(DestroyRef);

    readonly dataExportConfirmationForm = viewChild.required<NgForm>('dataExportConfirmationForm');

    // These are set from the dialog service, not as regular inputs
    dialogError: Observable<string>;
    dataExportRequest: OutputEmitterRef<void>;
    dataExportRequestForAnotherUser: OutputEmitterRef<string>;

    readonly submitDisabled = signal(false);
    readonly enteredLogin = signal('');
    readonly expectedLogin = signal('');
    readonly adminDialog = signal(false);
    readonly requestForAnotherUser = signal(false);
    readonly expectedLoginOfOtherUser = signal('');
    readonly confirmationTextHint = signal('artemisApp.dataExport.typeLoginToConfirm');

    ownLogin = '';

    protected readonly faBan = faBan;
    protected readonly faSpinner = faSpinner;
    protected readonly faCheck = faCheck;

    /**
     * Life cycle hook called by Angular to indicate that Angular is done creating the component
     */
    ngOnInit(): void {
        this.dialogError.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((errorMessage: string) => {
            if (errorMessage === '') {
                this.clear();
            } else {
                this.submitDisabled.set(false);
                this.alertService.error(errorMessage);
            }
        });
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
        this.submitDisabled.set(true);
        // we need to emit the login if it is a request by an admin for another user, so we can make the request for the data export using the login
        if (this.requestForAnotherUser()) {
            this.dataExportRequestForAnotherUser.emit(this.expectedLogin());
        } else {
            this.dataExportRequest.emit();
        }
    }

    onRequestDataExportForOtherUserChanged(event: Event): void {
        const target = event.target as HTMLInputElement;
        if (target.checked) {
            this.ownLogin = this.expectedLogin();
            this.expectedLogin.set(this.expectedLoginOfOtherUser() ?? '');
            this.confirmationTextHint.set('artemisApp.dataExport.typeUserLoginToConfirm');
            this.enteredLogin.set('');
        } else {
            this.enteredLogin.set('');
            this.expectedLogin.set(this.ownLogin);
            this.confirmationTextHint.set('artemisApp.dataExport.typeLoginToConfirm');
            this.expectedLoginOfOtherUser.set('');
        }
    }
}
