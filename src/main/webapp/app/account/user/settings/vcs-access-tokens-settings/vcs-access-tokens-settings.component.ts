import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { User } from 'app/account/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { Subject, Subscription, tap } from 'rxjs';
import dayjs from 'dayjs/esm';
import { faBan, faPlus, faSave, faTrash } from '@fortawesome/free-solid-svg-icons';
import { ButtonComponent, ButtonSize, ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { FormsModule } from '@angular/forms';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { FormDateTimePickerComponent } from 'app/shared-ui/date-time-picker/date-time-picker.component';
import { CopyToClipboardButtonComponent } from 'app/shared-ui/components/buttons/copy-to-clipboard-button/copy-to-clipboard-button.component';

@Component({
    selector: 'jhi-account-information',
    templateUrl: './vcs-access-tokens-settings.component.html',
    styleUrls: ['../user-settings.scss'],
    imports: [
        TranslateDirective,
        FaIconComponent,
        DeleteButtonDirective,
        ButtonComponent,
        FormDateTimePickerComponent,
        FormsModule,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        CopyToClipboardButtonComponent,
    ],
})
export class VcsAccessTokensSettingsComponent implements OnInit, OnDestroy {
    protected readonly faPlus = faPlus;
    protected readonly faSave = faSave;
    protected readonly faTrash = faTrash;
    protected readonly faBan = faBan;
    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;

    private accountService = inject(AccountService);
    private alertService = inject(AlertService);

    readonly currentUser = signal<User | undefined>(undefined);

    private authStateSubscription: Subscription;
    expiryDate?: dayjs.Dayjs;
    validExpiryDate = false;
    readonly edit = signal(false);

    private dialogErrorSource = new Subject<string>();

    dialogError$ = this.dialogErrorSource.asObservable();

    ngOnInit() {
        this.authStateSubscription = this.accountService
            .getAuthenticationState()
            .pipe(
                tap((user: User) => {
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
                    this.currentUser.set(Object.assign(new User(), current));
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
        if (!this.expiryDate || this.expiryDate.isBefore(dayjs()) || this.expiryDate.isAfter(dayjs().add(1, 'year'))) {
            this.alertService.error('artemisApp.userSettings.vcsAccessTokensSettingsPage.addFailure');
            return;
        }
        this.accountService.addNewVcsAccessToken(this.expiryDate.toISOString()).subscribe({
            next: (res) => {
                const current = this.currentUser();
                if (current) {
                    const user = res.body as User;
                    current.vcsAccessToken = user.vcsAccessToken;
                    current.vcsAccessTokenExpiryDate = user.vcsAccessTokenExpiryDate;
                    this.currentUser.set(Object.assign(new User(), current));
                    this.edit.set(false);
                }
                this.alertService.success('artemisApp.userSettings.vcsAccessTokensSettingsPage.addSuccess');
            },
            error: () => {
                this.alertService.error('artemisApp.userSettings.vcsAccessTokensSettingsPage.addFailure');
            },
        });
    }

    /**
     * Validates if the expiry date is after current time
     */
    validateDate() {
        this.validExpiryDate = !!this.expiryDate?.isAfter(dayjs()) && !!this.expiryDate?.isBefore(dayjs().add(1, 'year'));
    }

    /**
     *  Cancel creation of a new token
     */
    cancelTokenCreation() {
        this.edit.set(false);
        this.expiryDate = undefined;
        this.validExpiryDate = false;
    }
}
