import { Component, inject } from '@angular/core';
import { AccountInformationComponent } from 'app/account/user/settings/account-information/account-information.component';
import { GlobalNotificationsSettingsComponent } from 'app/account/user/settings/global-notifications-settings/global-notifications-settings.component';
import { isAtlasModuleActive } from 'app/account/user/settings/learner-profile/learner-profile-availability';
import { ScienceSettingsComponent } from 'app/account/user/settings/science-settings/science-settings.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

@Component({
    selector: 'jhi-profile-settings-v2',
    templateUrl: './profile-settings-v2.component.html',
    imports: [AccountInformationComponent, GlobalNotificationsSettingsComponent, ScienceSettingsComponent],
})
export class ProfileSettingsV2Component {
    private readonly profileService = inject(ProfileService);

    readonly isDataSharingAvailable = isAtlasModuleActive(this.profileService);
}
