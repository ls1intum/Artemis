import { Component, OnInit } from '@angular/core';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { Subject, Subscription, tap } from 'rxjs';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { PROFILE_LOCALVC } from 'app/app.constants';
import { faEdit, faSave, faTrash } from '@fortawesome/free-solid-svg-icons';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';

@Component({
    selector: 'jhi-account-information',
    templateUrl: './ssh-user-settings.component.html',
    styleUrls: ['../user-settings.scss'],
})
export class SshUserSettingsComponent implements OnInit {
    readonly documentationType: DocumentationType = 'SshSetup';
    currentUser?: User;
    localVCEnabled = false;
    sshKey = '';
    storedSshKey = '';
    editSshKey = false;

    faEdit = faEdit;
    faSave = faSave;
    faTrash = faTrash;
    private authStateSubscription: Subscription;
    private dialogErrorSource = new Subject<string>();

    dialogError$ = this.dialogErrorSource.asObservable();

    constructor(
        private accountService: AccountService,
        private profileService: ProfileService,
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
                    return this.currentUser;
                }),
            )
            .subscribe();
    }

    saveSshKey() {
        this.editSshKey = false;
        this.accountService.addSshPublicKey(this.sshKey).subscribe();
        this.storedSshKey = this.sshKey;
    }

    deleteSshKey() {
        this.editSshKey = false;
        this.accountService.deleteSshPublicKey().subscribe();
        this.sshKey = '';
        this.storedSshKey = '';
        this.dialogErrorSource.next('');
    }

    cancelEditingSshKey() {
        this.editSshKey = !this.editSshKey;
        this.sshKey = this.storedSshKey;
    }
}
