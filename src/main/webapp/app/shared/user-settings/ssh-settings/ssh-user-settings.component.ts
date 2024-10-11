import { Component, OnInit } from '@angular/core';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { Subject, Subscription, tap } from 'rxjs';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { PROFILE_LOCALVC } from 'app/app.constants';
import { faEdit, faSave, faTrash } from '@fortawesome/free-solid-svg-icons';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { AlertService } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-account-information',
    templateUrl: './ssh-user-settings.component.html',
    styleUrls: ['../user-settings.scss', './ssh-user-settings.component.scss'],
})
export class SshUserSettingsComponent implements OnInit {
    readonly documentationType: DocumentationType = 'SshSetup';
    currentUser?: User;
    localVCEnabled = false;
    sshKey = '';
    storedSshKey = '';
    editSshKey = false;
    keyCount = 0;

    readonly faEdit = faEdit;
    readonly faSave = faSave;
    readonly faTrash = faTrash;
    private authStateSubscription: Subscription;
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
                    this.storedSshKey = user.sshPublicKey || '';
                    this.sshKey = this.storedSshKey;
                    this.currentUser = user;
                    // currently only 0 or 1 key are supported
                    this.keyCount = this.sshKey ? 1 : 0;
                    return this.currentUser;
                }),
            )
            .subscribe();
    }

    saveSshKey() {
        this.accountService.addSshPublicKey(this.sshKey).subscribe({
            next: () => {
                this.alertService.success('artemisApp.userSettings.sshSettingsPage.saveSuccess');
                this.editSshKey = false;
                this.storedSshKey = this.sshKey;
            },
            error: () => {
                this.alertService.error('artemisApp.userSettings.sshSettingsPage.saveFailure');
            },
        });
    }

    deleteSshKey() {
        this.editSshKey = false;
        this.accountService.deleteSshPublicKey().subscribe({
            next: () => {
                this.alertService.success('artemisApp.userSettings.sshSettingsPage.deleteSuccess');
                this.sshKey = '';
                this.storedSshKey = '';
            },
            error: () => {
                this.alertService.error('artemisApp.userSettings.sshSettingsPage.deleteFailure');
            },
        });
        this.dialogErrorSource.next('');
    }

    cancelEditingSshKey() {
        this.editSshKey = !this.editSshKey;
        this.sshKey = this.storedSshKey;
    }

    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;
}
