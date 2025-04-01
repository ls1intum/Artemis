import { Component, OnInit, inject } from '@angular/core';
import { UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { faInfoCircle, faSave } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { UserSettingsDirective } from 'app/core/user/settings/user-settings.directive';
import { NotificationSettingsService, reloadNotificationSideBarMessage } from 'app/core/user/settings/notification-settings/notification-settings.service';
import { UserSettingsStructure } from 'app/core/user/settings/user-settings.model';
import { NotificationSetting } from 'app/core/user/settings/notification-settings/notification-settings-structure';

export enum NotificationSettingsCommunicationChannel {
    WEBAPP,
    EMAIL,
}

@Component({
    selector: 'jhi-notification-settings',
    templateUrl: 'notification-settings.component.html',
    styleUrls: ['../user-settings.scss'],
    imports: [
        FaIconComponent,
        TranslateDirective,
        // NOTE: this is actually used in the html template, otherwise *jhiHasAnyAuthority would not work
        HasAnyAuthorityDirective,
        ArtemisTranslatePipe,
    ],
})
export class NotificationSettingsComponent extends UserSettingsDirective implements OnInit {
    private notificationSettingsService = inject(NotificationSettingsService);

    // Icons
    faSave = faSave;
    faInfoCircle = faInfoCircle;

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
