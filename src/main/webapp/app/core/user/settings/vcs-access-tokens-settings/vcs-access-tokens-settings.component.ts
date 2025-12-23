import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { Subject, Subscription, tap } from 'rxjs';
import dayjs from 'dayjs/esm';
import { faBan, faPlus, faSave, faTrash } from '@fortawesome/free-solid-svg-icons';
import { ButtonComponent, ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { FormsModule } from '@angular/forms';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { CopyToClipboardButtonComponent } from 'app/shared/components/buttons/copy-to-clipboard-button/copy-to-clipboard-button.component';

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

    currentUser?: User;

    private authStateSubscription: Subscription;
    expiryDate?: dayjs.Dayjs;
    validExpiryDate = false;
    edit = false;

    private dialogErrorSource = new Subject<string>();

    dialogError$ = this.dialogErrorSource.asObservable();

    ngOnInit() {
        this.authStateSubscription = this.accountService
            .getAuthenticationState()
            .pipe(
                tap((user: User) => {
                    this.currentUser = user;
                    return this.currentUser;
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
                if (this.currentUser) {
                    this.currentUser.vcsAccessTokenExpiryDate = undefined;
                    this.currentUser.vcsAccessToken = undefined;
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
        this.edit = true;
    }

    sendTokenCreationRequest() {
        if (!this.expiryDate || this.expiryDate.isBefore(dayjs()) || this.expiryDate.isAfter(dayjs().add(1, 'year'))) {
            this.alertService.error('artemisApp.userSettings.vcsAccessTokensSettingsPage.addFailure');
            return;
        }
        this.accountService.addNewVcsAccessToken(this.expiryDate.toISOString()).subscribe({
            next: (res) => {
                if (this.currentUser) {
                    const user = res.body as User;
                    this.currentUser.vcsAccessToken = user.vcsAccessToken;
                    this.currentUser.vcsAccessTokenExpiryDate = user.vcsAccessTokenExpiryDate;
                    this.edit = false;
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
        this.edit = false;
        this.expiryDate = undefined;
        this.validExpiryDate = false;
    }
}
