import { NgModule, TemplateRef } from '@angular/core';
import { UserSettingsContainerComponent } from 'app/shared/user-settings/user-settings-container/user-settings-container.component';
import { AccountInformationComponent } from 'app/shared/user-settings/account-information/account-information.component';
import { NotificationSettingsComponent } from 'app/shared/user-settings/notification-settings/notification-settings.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RouterModule } from '@angular/router';
import { userSettingsState } from 'app/shared/user-settings/user-settings.route';
import { UserSettingsPrototypeComponent } from 'app/shared/user-settings/user-settings-prototype/user-settings-prototype.component';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';

@NgModule({
    imports: [RouterModule.forChild(userSettingsState), ArtemisSharedModule],
    declarations: [UserSettingsContainerComponent, UserSettingsPrototypeComponent, AccountInformationComponent, NotificationSettingsComponent],
    exports: [],
})
export class UserSettingsModule {}
