import { Component, OnInit } from '@angular/core';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { Subject, Subscription, tap } from 'rxjs';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { PROFILE_LOCALVC } from 'app/app.constants';
import { faCopy, faEdit, faSave, faTrash } from '@fortawesome/free-solid-svg-icons';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { AlertService } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-account-information',
    templateUrl: './vcs-access-tokens-settings.html',
    styleUrls: ['../user-settings.scss'],
})
export class VcsAccessTokensSettingsComponent implements OnInit {
    currentUser?: User;
    localVCEnabled = false;
    editSshKey = false;

    readonly faEdit = faEdit;
    readonly faSave = faSave;
    readonly faTrash = faTrash;
    readonly faCopy = faCopy;
    private authStateSubscription: Subscription;
    wasCopied = false;

    private dialogErrorSource = new Subject<string>();

    dialogError$ = this.dialogErrorSource.asObservable();

    constructor(
        private accountService: AccountService,
        private profileService: ProfileService,
        private alertService: AlertService,
    ) {}

    ngOnInit() {
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            this.localVCEnabled = profileInfo.activeProfiles.includes(PROFILE_LOCALVC);
        });

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

    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;

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
        this.accountService.addNewVcsAccessToken().subscribe({
            next: (res) => {
                if (this.currentUser) {
                    const user = res.body as User;
                    this.currentUser.vcsAccessToken = user.vcsAccessToken;
                    this.currentUser!.vcsAccessTokenExpiryDate = user.vcsAccessTokenExpiryDate;
                }
                this.alertService.success('artemisApp.userSettings.vcsAccessTokensSettingsPage.addSuccess');
            },
            error: () => {
                this.alertService.error('artemisApp.userSettings.vcsAccessTokensSettingsPage.addFailure');
            },
        });
    }
    /**
     * set wasCopied for 3 seconds on success
     */
    onCopyFinished(successful: any) {
        if (successful) {
            this.wasCopied = true;
            setTimeout(() => {
                this.wasCopied = false;
            }, 3000);
        }
    }
}
