import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';
import { Subject, Subscription, tap } from 'rxjs';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { PROFILE_LOCALVC } from 'app/app.constants';
import { faEdit, faEllipsis, faSave, faTrash } from '@fortawesome/free-solid-svg-icons';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { AlertService } from 'app/core/util/alert.service';
import { UserSshPublicKey } from 'app/entities/programming/user-ssh-public-key.model';
import { NgbDropdown } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-account-information',
    templateUrl: './ssh-user-settings.component.html',
    styleUrls: ['../user-settings.scss', './ssh-user-settings.component.scss'],
})
export class SshUserSettingsComponent implements OnInit, OnDestroy {
    readonly documentationType: DocumentationType = 'SshSetup';

    sshPublicKeys: UserSshPublicKey[] = [];
    localVCEnabled = false;

    keyCount = 0;
    isLoading = true;

    readonly faEdit = faEdit;
    readonly faSave = faSave;
    readonly faTrash = faTrash;
    readonly faEllipsis = faEllipsis;
    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;

    private accountServiceSubscription: Subscription;
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    @ViewChild('itemsDrop', { static: true }) itemsDrop: NgbDropdown;

    constructor(
        private accountService: AccountService,
        private profileService: ProfileService,
        private alertService: AlertService,
    ) {}

    ngOnInit() {
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            this.localVCEnabled = profileInfo.activeProfiles.includes(PROFILE_LOCALVC);
        });
        this.accountServiceSubscription = this.accountService
            .getAllSshPublicKeys()
            .pipe(
                tap((publicKeys: UserSshPublicKey[]) => {
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

    deleteSshKey(key: UserSshPublicKey) {
        this.accountService.deleteSshPublicKey(key.id).subscribe({
            next: () => {
                this.alertService.success('artemisApp.userSettings.sshSettingsPage.deleteSuccess');
                this.keyCount = this.keyCount - 1;
                const index = this.sshPublicKeys.indexOf(key);
                if (index >= 0) {
                    this.sshPublicKeys.splice(index, 1);
                }
            },
            error: (error) => {
                console.log(error);
                this.alertService.error('artemisApp.userSettings.sshSettingsPage.deleteFailure');
            },
        });
        this.dialogErrorSource.next('');
    }
}
