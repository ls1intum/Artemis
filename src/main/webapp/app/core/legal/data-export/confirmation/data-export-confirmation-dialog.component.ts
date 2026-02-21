import { Component, DestroyRef, OnInit, inject, input, model, output, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Observable } from 'rxjs';
import { AlertService } from 'app/shared/service/alert.service';
import { FormsModule, NgForm } from '@angular/forms';
import { faBan, faCheck, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ConfirmEntityNameComponent } from 'app/shared/confirm-entity-name/confirm-entity-name.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TypeAheadUserSearchFieldComponent } from 'app/core/legal/data-export/type-ahead-search-field/type-ahead-user-search-field.component';
import { User } from 'app/core/user/user.model';
import { DialogModule } from 'primeng/dialog';

@Component({
    selector: 'jhi-data-export-confirmation-dialog',
    templateUrl: './data-export-confirmation-dialog.component.html',
    imports: [FormsModule, TranslateDirective, TypeAheadUserSearchFieldComponent, ConfirmEntityNameComponent, FaIconComponent, DialogModule],
})
export class DataExportConfirmationDialogComponent implements OnInit {
    private readonly alertService = inject(AlertService);
    private readonly destroyRef = inject(DestroyRef);

    readonly dataExportConfirmationForm = viewChild.required<NgForm>('dataExportConfirmationForm');

    /** Two-way bound visibility for the p-dialog */
    readonly visible = model<boolean>(false);

    /** Input for the dialog error observable */
    readonly dialogError = input<Observable<string>>(EMPTY);

    /** Input for whether this is an admin dialog */
    readonly adminDialog = input(false);

    /** Input for the expected login */
    readonly expectedLoginInput = input<string>('', { alias: 'expectedLogin' }); // eslint-disable-line @angular-eslint/no-input-rename

    /** Output events replacing the old OutputEmitterRef approach */
    readonly dataExportRequest = output<void>();
    readonly dataExportRequestForAnotherUser = output<string>();

    readonly submitDisabled = signal(false);
    readonly enteredLogin = signal('');
    readonly expectedLogin = signal('');
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
        this.expectedLogin.set(this.expectedLoginInput());
        this.dialogError()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((errorMessage: string) => {
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
        this.visible.set(false);
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
        const target = event.target as HTMLInputElement | null;
        if (!target) {
            return;
        }
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

    onLoginOrNameChange(value: string | User): void {
        const login = typeof value === 'string' ? value : (value.login ?? '');
        this.expectedLogin.set(login);
        // Also update expectedLoginOfOtherUser so toggling the checkbox preserves the selection
        if (this.requestForAnotherUser()) {
            this.expectedLoginOfOtherUser.set(login);
        }
    }
}
