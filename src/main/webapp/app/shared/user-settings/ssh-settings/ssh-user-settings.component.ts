import { Component, OnDestroy, OnInit } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';
import { Subject, Subscription, tap } from 'rxjs';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { PROFILE_LOCALVC } from 'app/app.constants';
import { faEdit, faEllipsis, faSave, faTrash } from '@fortawesome/free-solid-svg-icons';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { AlertService } from 'app/core/util/alert.service';
import { getOS } from 'app/shared/util/os-detector.util';
import { UserSshPublicKey } from 'app/entities/programming/user-ssh-public-key.model';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-account-information',
    templateUrl: './ssh-user-settings.component.html',
    styleUrls: ['../user-settings.scss', './ssh-user-settings.component.scss'],
})
export class SshUserSettingsComponent implements OnInit, OnDestroy {
    readonly documentationType: DocumentationType = 'SshSetup';

    sshPublicKeys: UserSshPublicKey[] = [];
    localVCEnabled = false;

    // state change variables
    showKeyDetailsView = false;
    inCreateMode = false; // true when editing existing key, false when creating new key

    keyCount = 0;
    isLoading = true;
    copyInstructions = '';

    // Key details from input fields
    displayedKeyId?: number = undefined; // undefined when creating a new key
    displayedKeyLabel = '';
    displayedSshKey = '';
    displayedKeyHash = '';
    displayedExpiryDate?: dayjs.Dayjs;

    readonly faEdit = faEdit;
    readonly faSave = faSave;
    readonly faTrash = faTrash;
    readonly faEllipsis = faEllipsis;
    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;

    private accountServiceSubscription: Subscription;
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
        this.accountServiceSubscription = this.accountService
            .getAllSshPublicKeys()
            .pipe(
                tap((publicKeys: UserSshPublicKey[]) => {
                    this.alertService.success('artemisApp.userSettings.sshSettingsPage.saveSuccess');
                    this.sshPublicKeys = publicKeys;
                    this.keyCount = publicKeys.length;
                    this.isLoading = false;
                }),
            )
            .subscribe();
    }

    ngOnDestroy() {
        this.accountServiceSubscription.unsubscribe();
    }

    saveSshKey() {
        const newUserSshKey = {
            id: this.displayedKeyId,
            label: this.displayedKeyLabel,
            publicKey: this.displayedSshKey,
            expiryDate: this.displayedExpiryDate,
        } as UserSshPublicKey;

        this.accountService.addNewSshPublicKey(newUserSshKey).subscribe({
            next: (res) => {
                const newlyCreatedKey = res.body!;
                this.sshPublicKeys.push(newlyCreatedKey);
                this.showKeyDetailsView = false;
                this.keyCount = this.keyCount + 1;
                this.inCreateMode = true;
                this.alertService.success('artemisApp.userSettings.sshSettingsPage.saveSuccess');
            },
            error: () => {
                this.alertService.error('artemisApp.userSettings.sshSettingsPage.saveFailure');
            },
        });
    }

    deleteSshKey() {
        this.showKeyDetailsView = false;
        this.accountService.deleteSshPublicKey('ab').subscribe({
            next: () => {
                this.alertService.success('artemisApp.userSettings.sshSettingsPage.deleteSuccess');
                this.keyCount = this.keyCount - 1;
                this.inCreateMode = false;
            },
            error: () => {
                this.alertService.error('artemisApp.userSettings.sshSettingsPage.deleteFailure');
            },
        });
        this.dialogErrorSource.next('');
    }

    cancelEditingSshKey() {
        this.showKeyDetailsView = false;
    }

    editExistingSshKey(key: UserSshPublicKey) {
        this.showKeyDetailsView = true;
        this.inCreateMode = false;
        this.displayedKeyId = key.id;
        this.displayedSshKey = key.publicKey;
        this.displayedKeyLabel = key.label;
        this.displayedKeyHash = key.keyHash;
        this.displayedExpiryDate = key.expiryDate;
    }

    createNewSshKey() {
        this.showKeyDetailsView = true;
        this.inCreateMode = false;
        this.displayedKeyId = undefined;
    }

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
