import { NgModule } from '@angular/core';
import { UserSettingsContainerComponent } from 'app/shared/user-settings/user-settings-container/user-settings-container.component';
import { AccountInformationComponent } from 'app/shared/user-settings/account-information/account-information.component';
import { NotificationSettingsComponent } from 'app/shared/user-settings/notification-settings/notification-settings.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RouterModule } from '@angular/router';
import { userSettingsState } from 'app/shared/user-settings/user-settings.route';
import { PersonalAccessTokensComponent } from './personal-access-tokens/personal-access-tokens.component';
import { FeatureToggleModule } from '../feature-toggle/feature-toggle.module';
import { ClipboardModule } from '@angular/cdk/clipboard';

@NgModule({
    imports: [RouterModule.forChild(userSettingsState), ArtemisSharedModule, FeatureToggleModule, ClipboardModule],
    declarations: [UserSettingsContainerComponent, AccountInformationComponent, NotificationSettingsComponent, PersonalAccessTokensComponent],
})
export class UserSettingsModule {}
