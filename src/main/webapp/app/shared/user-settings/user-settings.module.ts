import { NgModule } from '@angular/core';
import { AccountInformationComponent } from 'app/shared/user-settings/account-information/account-information.component';
import { NotificationSettingsComponent } from 'app/shared/user-settings/notification-settings/notification-settings.component';

import { RouterModule } from '@angular/router';
import { userSettingsState } from 'app/shared/user-settings/user-settings.route';
import { ScienceSettingsComponent } from 'app/shared/user-settings/science-settings/science-settings.component';
import { SshUserSettingsComponent } from 'app/shared/user-settings/ssh-settings/ssh-user-settings.component';

import { VcsAccessTokensSettingsComponent } from 'app/shared/user-settings/vcs-access-tokens-settings/vcs-access-tokens-settings.component';
import { ClipboardModule } from '@angular/cdk/clipboard';
import { IdeSettingsComponent } from 'app/shared/user-settings/ide-preferences/ide-settings.component';
import { DocumentationLinkComponent } from 'app/shared/components/documentation-link/documentation-link.component';
import { SshUserSettingsKeyDetailsComponent } from 'app/shared/user-settings/ssh-settings/details/ssh-user-settings-key-details.component';
import { SshUserSettingsFingerprintsComponent } from 'app/shared/user-settings/ssh-settings/fingerprints/ssh-user-settings-fingerprints.component';

@NgModule({
    imports: [
        RouterModule.forChild(userSettingsState),

        ClipboardModule,
        DocumentationLinkComponent,
        AccountInformationComponent,
        NotificationSettingsComponent,
        ScienceSettingsComponent,
        SshUserSettingsComponent,
        SshUserSettingsKeyDetailsComponent,
        SshUserSettingsFingerprintsComponent,
        VcsAccessTokensSettingsComponent,
        IdeSettingsComponent,
    ],
})
export class UserSettingsModule {}
