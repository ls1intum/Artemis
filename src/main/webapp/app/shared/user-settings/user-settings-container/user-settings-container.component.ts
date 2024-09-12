import { Component, OnInit } from '@angular/core';
import { faUser } from '@fortawesome/free-solid-svg-icons';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { PROFILE_LOCALVC } from 'app/app.constants';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { tap } from 'rxjs';

/**
 * UserSettingsContainerComponent serves as the common ground for different settings
 */
@Component({
    selector: 'jhi-user-settings',
    templateUrl: 'user-settings-container.component.html',
    styleUrls: ['user-settings-container.component.scss'],
})
export class UserSettingsContainerComponent implements OnInit {
    // Icons
    faUser = faUser;

    currentUser?: User;
    localVCEnabled = false;

    constructor(
        private profileService: ProfileService,
        private accountService: AccountService,
    ) {}

    ngOnInit() {
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            this.localVCEnabled = profileInfo.activeProfiles.includes(PROFILE_LOCALVC);
        });

        this.accountService
            .getAuthenticationState()
            .pipe(tap((user: User) => (this.currentUser = user)))
            .subscribe();
    }
}
