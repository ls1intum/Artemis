import { Component, OnInit } from '@angular/core';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { Subscription, tap } from 'rxjs';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { PROFILE_LOCALVC } from 'app/app.constants';
import { faEdit, faSave } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-account-information',
    templateUrl: './account-information.component.html',
    styleUrls: ['../user-settings.scss'],
})
export class AccountInformationComponent implements OnInit {
    currentUser?: User;
    localVCEnabled: boolean = false;
    sshKey: string = '';
    editSshKey = false;

    faEdit = faEdit;
    faSave = faSave;

    private authStateSubscription: Subscription;

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
                    this.sshKey = user.sshPublicKey || '';
                    return (this.currentUser = user);
                }),
            )
            .subscribe();
    }

    saveSshKey() {
        this.editSshKey = false;
        this.accountService.addSshPublicKey(this.sshKey).subscribe();
    }
}
