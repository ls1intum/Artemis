import { Component, OnInit } from '@angular/core';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { Subject, Subscription, tap } from 'rxjs';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { PROFILE_LOCALVC } from 'app/app.constants';
import { faEdit, faEllipsis, faSave, faTrash } from '@fortawesome/free-solid-svg-icons';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { AlertService } from 'app/core/util/alert.service';
import { getOS } from 'app/shared/util/os-detector.util';

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
    sshKeyHash = '';
    storedSshKey = '';
    showSshKey = false;
    keyCount = 0;
    isKeyReadonly = true;
    copyInstructions = '';

    readonly faEdit = faEdit;
    readonly faSave = faSave;
    readonly faTrash = faTrash;
    readonly faEllipsis = faEllipsis;

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
        this.setMessageBasedOnOS(getOS());
        this.authStateSubscription = this.accountService
            .getAuthenticationState()
            .pipe(
                tap((user: User) => {
                    this.storedSshKey = user.sshPublicKey || '';
                    this.sshKey = this.storedSshKey;
                    this.sshKeyHash = user.sshKeyHash || '';
                    this.currentUser = user;
                    // currently only 0 or 1 key are supported
                    this.keyCount = this.sshKey ? 1 : 0;
                    this.isKeyReadonly = !!this.sshKey;
                    return this.currentUser;
                }),
            )
            .subscribe();
    }

    saveSshKey() {
        this.accountService.addSshPublicKey(this.sshKey).subscribe({
            next: () => {
                this.alertService.success('artemisApp.userSettings.sshSettingsPage.saveSuccess');
                this.showSshKey = false;
                this.storedSshKey = this.sshKey;
                this.keyCount = this.keyCount + 1;
                this.isKeyReadonly = true;
            },
            error: () => {
                this.alertService.error('artemisApp.userSettings.sshSettingsPage.saveFailure');
            },
        });
    }

    deleteSshKey() {
        this.showSshKey = false;
        this.accountService.deleteSshPublicKey().subscribe({
            next: () => {
                this.alertService.success('artemisApp.userSettings.sshSettingsPage.deleteSuccess');
                this.sshKey = '';
                this.storedSshKey = '';
                this.keyCount = this.keyCount - 1;
                this.isKeyReadonly = false;
            },
            error: () => {
                this.alertService.error('artemisApp.userSettings.sshSettingsPage.deleteFailure');
            },
        });
        this.dialogErrorSource.next('');
    }

    cancelEditingSshKey() {
        this.showSshKey = !this.showSshKey;
        this.sshKey = this.storedSshKey;
    }

    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;

    private setMessageBasedOnOS(os: string): void {
        switch (os) {
            case 'Windows':
                this.copyInstructions = 'cat ~/.ssh/id_ed25519.pub | clip';
                break;
            case 'MacOS':
                this.copyInstructions = 'pbcopy < ~/.ssh/id_ed25519.pub';
                break;
            case 'Linux':
                this.copyInstructions = 'xclip -selection clipboard < ~/.ssh/id_ed25519.pub';
                break;
            case 'Android':
                this.copyInstructions = 'termux-clipboard-set < ~/.ssh/id_ed25519.pub';
                break;
            default:
                this.copyInstructions = 'Ctrl + C';
        }
    }
}
