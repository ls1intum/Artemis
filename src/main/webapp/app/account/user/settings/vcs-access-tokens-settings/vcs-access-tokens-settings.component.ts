import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { User } from 'app/account/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { Subject, Subscription, tap } from 'rxjs';
import dayjs from 'dayjs/esm';
import { faBan, faPlus, faSave, faTrash } from '@fortawesome/free-solid-svg-icons';
import {
    TumAetUiButtonComponent,
    TumAetUiButtonDirective,
    TumAetUiDatePickerComponent,
    TumAetUiListComponent,
    TumAetUiListItemDirective,
    TumAetUiTableDirective,
} from '@tumaet/ui-angular';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { CopyToClipboardButtonComponent } from 'app/shared-ui/components/buttons/copy-to-clipboard-button/copy-to-clipboard-button.component';

@Component({
    selector: 'jhi-account-information',
    templateUrl: './vcs-access-tokens-settings.component.html',
    styleUrls: ['../user-settings.scss'],
    imports: [
        TranslateDirective,
        FaIconComponent,
        DeleteButtonDirective,
        TumAetUiButtonComponent,
        TumAetUiButtonDirective,
        TumAetUiListComponent,
        TumAetUiListItemDirective,
        TumAetUiTableDirective,
        TumAetUiDatePickerComponent,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        CopyToClipboardButtonComponent,
    ],
})
export class VcsAccessTokensSettingsComponent implements OnInit, OnDestroy {
    private accountService = inject(AccountService);
    private alertService = inject(AlertService);

    protected readonly faPlus = faPlus;
    protected readonly faSave = faSave;
    protected readonly faTrash = faTrash;
    protected readonly faBan = faBan;

    // `equal: () => false` so re-setting the same reference emits after the token fields are assigned in place.
    readonly currentUser = signal<User | undefined>(undefined, { equal: () => false });

    private authStateSubscription!: Subscription; // assigned in ngOnInit(), before ngOnDestroy() unsubscribes
    readonly expiryDate = signal<dayjs.Dayjs | undefined>(undefined);
    readonly validExpiryDate = signal(false);
    /** The picker keeps its last valid date while the typed text is not a date, so the text validity is tracked separately. */
    private expiryTextValid = true;
    readonly edit = signal(false);

    private dialogErrorSource = new Subject<string>();

    dialogError$ = this.dialogErrorSource.asObservable();

    ngOnInit() {
        this.authStateSubscription = this.accountService
            .getAuthenticationState()
            .pipe(
                tap((user: User | undefined) => {
                    this.currentUser.set(user);
                    return user;
                }),
            )
            .subscribe();
    }

    ngOnDestroy(): void {
        this.authStateSubscription.unsubscribe();
    }

    deleteVcsAccessToken() {
        this.accountService.deleteUserVcsAccessToken().subscribe({
            next: () => {
                const current = this.currentUser();
                if (current) {
                    current.vcsAccessTokenExpiryDate = undefined;
                    current.vcsAccessToken = undefined;
                    this.currentUser.set(current);
                }
                this.alertService.success('artemisApp.userSettings.vcsAccessTokensSettingsPage.deleteSuccess');
            },
            error: () => {
                this.alertService.error('artemisApp.userSettings.vcsAccessTokensSettingsPage.deleteFailure');
            },
        });
        this.dialogErrorSource.next('');
    }

    addNewVcsAccessToken() {
        this.edit.set(true);
    }

    sendTokenCreationRequest() {
        const expiryDate = this.expiryDate();
        if (!expiryDate || expiryDate.isBefore(dayjs()) || expiryDate.isAfter(dayjs().add(1, 'year'))) {
            this.alertService.error('artemisApp.userSettings.vcsAccessTokensSettingsPage.addFailure');
            return;
        }
        this.accountService.addNewVcsAccessToken(expiryDate.toISOString()).subscribe({
            next: (res) => {
                const current = this.currentUser();
                if (current) {
                    const user = res.body as User;
                    current.vcsAccessToken = user.vcsAccessToken;
                    current.vcsAccessTokenExpiryDate = user.vcsAccessTokenExpiryDate;
                    this.currentUser.set(current);
                    this.edit.set(false);
                }
                this.alertService.success('artemisApp.userSettings.vcsAccessTokensSettingsPage.addSuccess');
            },
            error: () => {
                this.alertService.error('artemisApp.userSettings.vcsAccessTokensSettingsPage.addFailure');
            },
        });
    }

    onExpiryDateChange(date: dayjs.Dayjs | undefined) {
        this.expiryDate.set(date);
        this.validateDate();
    }

    onExpiryTextValidityChange(isValid: boolean) {
        this.expiryTextValid = isValid;
        this.validateDate();
    }

    /**
     * Validates that the entered text is a date and that the expiry date is after the current time and within one year
     */
    validateDate() {
        const expiryDate = this.expiryDate();
        this.validExpiryDate.set(this.expiryTextValid && !!expiryDate?.isAfter(dayjs()) && !!expiryDate?.isBefore(dayjs().add(1, 'year')));
    }

    /**
     *  Cancel creation of a new token
     */
    cancelTokenCreation() {
        this.edit.set(false);
        this.expiryDate.set(undefined);
        this.expiryTextValid = true;
        this.validExpiryDate.set(false);
    }
}
