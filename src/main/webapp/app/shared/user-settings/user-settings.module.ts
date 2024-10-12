import { NgModule } from '@angular/core';
import { UserSettingsContainerComponent } from 'app/shared/user-settings/user-settings-container/user-settings-container.component';
import { AccountInformationComponent } from 'app/shared/user-settings/account-information/account-information.component';
import { NotificationSettingsComponent } from 'app/shared/user-settings/notification-settings/notification-settings.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RouterModule } from '@angular/router';
import { userSettingsState } from 'app/shared/user-settings/user-settings.route';
import { ScienceSettingsComponent } from 'app/shared/user-settings/science-settings/science-settings.component';
import { SshUserSettingsComponent } from 'app/shared/user-settings/ssh-settings/ssh-user-settings.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { VcsAccessTokensSettingsComponent } from 'app/shared/user-settings/vcs-access-tokens-settings/vcs-access-tokens-settings.component';
import { ClipboardModule } from '@angular/cdk/clipboard';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { IdeSettingsComponent } from 'app/shared/user-settings/ide-preferences/ide-settings.component';
import { DocumentationLinkComponent } from 'app/shared/components/documentation-link/documentation-link.component';

@NgModule({
    imports: [RouterModule.forChild(userSettingsState), ArtemisSharedModule, ArtemisSharedComponentModule, ClipboardModule, FormDateTimePickerModule, DocumentationLinkComponent],
    declarations: [
        UserSettingsContainerComponent,
        AccountInformationComponent,
        NotificationSettingsComponent,
        ScienceSettingsComponent,
        SshUserSettingsComponent,
        VcsAccessTokensSettingsComponent,
        IdeSettingsComponent,
    ],
})
export class UserSettingsModule {}
