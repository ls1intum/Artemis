import { NgModule } from '@angular/core';
import { UserSettingsComponent } from 'app/shared/user-settings/user-settings.component';
import { AccountInformationComponent } from 'app/shared/user-settings/account-information/account-information.component';
import { NotificationSettingsComponent } from 'app/shared/user-settings/notification-settings/notification-settings.component';
import { UserSettingsService } from 'app/shared/user-settings/user-settings.service';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RouterModule } from '@angular/router';
import { userSettingsState } from 'app/shared/user-settings/user-settings.route';
import { UserSettingsPrototypeComponent } from 'app/shared/user-settings/user-settings-prototype/user-settings-prototype.component';

@NgModule({
    imports: [RouterModule.forChild(userSettingsState), ArtemisSharedModule],
    declarations: [UserSettingsComponent, UserSettingsPrototypeComponent, AccountInformationComponent, NotificationSettingsComponent],
    exports: [],
    providers: [UserSettingsService],
})
export class UserSettingsModule {}
