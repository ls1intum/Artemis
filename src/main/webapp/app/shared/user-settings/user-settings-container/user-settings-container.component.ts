import { Component, OnInit } from '@angular/core';
import { faUser } from '@fortawesome/free-solid-svg-icons';
import { AccountService } from 'app/core/auth/account.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { AccountInformationComponent } from 'app/shared/user-settings/account-information/account-information.component';

/**
 * UserSettingsContainerComponent serves as the common ground for different settings
 */
@Component({
    selector: 'jhi-user-settings',
    templateUrl: 'user-settings-container.component.html',
    styleUrls: ['user-settings-container.component.scss'],
})
export class UserSettingsContainerComponent extends AccountInformationComponent implements OnInit {
    // Icons
    faUser = faUser;
    patEnabled = false;

    constructor(accountService: AccountService, private profileService: ProfileService) {
        super(accountService);
    }

    ngOnInit() {
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            this.patEnabled = profileInfo.activeProfiles.includes('pat');
        });
    }
}
