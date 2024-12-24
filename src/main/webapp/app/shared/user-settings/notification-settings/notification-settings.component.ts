import { Component, OnInit, inject } from '@angular/core';
import { UserSettingsDirective } from 'app/shared/user-settings/user-settings.directive';
import { UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { NotificationSetting } from 'app/shared/user-settings/notification-settings/notification-settings-structure';
import { UserSettingsStructure } from 'app/shared/user-settings/user-settings.model';
import { faInfoCircle, faSave } from '@fortawesome/free-solid-svg-icons';
import { NotificationSettingsService, reloadNotificationSideBarMessage } from 'app/shared/user-settings/notification-settings/notification-settings.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { RouterModule } from '@angular/router';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { DocumentationLinkComponent } from 'app/shared/components/documentation-link/documentation-link.component';

export enum NotificationSettingsCommunicationChannel {
    WEBAPP,
    EMAIL,
}

@Component({
    selector: 'jhi-notification-settings',
    templateUrl: 'notification-settings.component.html',
    standalone: true,
    styleUrls: ['../user-settings.scss'],
    imports: [TranslateDirective, RouterModule, FontAwesomeModule, ArtemisSharedModule, ArtemisSharedComponentModule, FormDateTimePickerModule, DocumentationLinkComponent],
})
export class NotificationSettingsComponent extends UserSettingsDirective implements OnInit {
    private notificationSettingsService = inject(NotificationSettingsService);

    // Icons
    protected readonly faSave = faSave;
    protected readonly faInfoCircle = faInfoCircle;

    declare userSettings: UserSettingsStructure<NotificationSetting>;
    declare settings: Array<NotificationSetting>;

    // needed for HTML access
    readonly communicationChannel = NotificationSettingsCommunicationChannel;

    ngOnInit(): void {
        this.userSettingsCategory = UserSettingsCategory.NOTIFICATION_SETTINGS;
        this.changeEventMessage = reloadNotificationSideBarMessage;

        // check if settings are already loaded
        const newestNotificationSettings: NotificationSetting[] = this.notificationSettingsService.getNotificationSettings();
        if (newestNotificationSettings.length === 0) {
            // if no settings are already available load them from the server
            super.ngOnInit();
        } else {
            // else reuse the already available/loaded ones
            this.userSettings = this.userSettingsService.loadSettingsSuccessAsSettingsStructure(newestNotificationSettings, this.userSettingsCategory);
            this.settings = this.userSettingsService.extractIndividualSettingsFromSettingsStructure(this.userSettings);
            this.changeDetector.detectChanges();
        }
    }

    /**
     * Catches the toggle event from a user click
     * Toggles the respective setting and mark it as changed (only changed settings will be send to the server for saving)
     */
    toggleSetting(event: any, communicationChannel: NotificationSettingsCommunicationChannel) {
        this.settingsChanged = true;
        let settingId = event.currentTarget.id;
        // optionId String could have an appended (e.g.( " email" or " webapp" to specify which option to toggle
        if (settingId.indexOf(' ') !== -1) {
            settingId = settingId.slice(0, settingId.indexOf(' '));
        }
        const settingToUpdate = this.settings.find((setting) => setting.settingId === settingId);
        if (!settingToUpdate) {
            return;
        }
        // toggle/inverts previous setting
        switch (communicationChannel) {
            case NotificationSettingsCommunicationChannel.WEBAPP: {
                settingToUpdate!.webapp = !settingToUpdate!.webapp;
                break;
            }
            case NotificationSettingsCommunicationChannel.EMAIL: {
                settingToUpdate!.email = !settingToUpdate!.email;
                break;
            }
        }
        settingToUpdate.changed = true;
    }
}
