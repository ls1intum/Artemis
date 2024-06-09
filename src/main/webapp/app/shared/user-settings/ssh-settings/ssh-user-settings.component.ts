import { Component, OnInit } from '@angular/core';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { Subject, Subscription, tap } from 'rxjs';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { PROFILE_LOCALVC } from 'app/app.constants';
import { faEdit, faLink, faSave, faTrash } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-account-information',
    templateUrl: './ssh-user-settings.component.html',
    styleUrls: ['../user-settings.scss'],
})
export class SshUserSettingsComponent implements OnInit {
    currentUser?: User;
    localVCEnabled: boolean = false;
    sshKey: string = '';
    storedSshKey: string = '';
    editSshKey = false;

    faEdit = faEdit;
    faSave = faSave;
    faTrash = faTrash;
    faLink = faLink;
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
                    return (this.currentUser = user);
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
