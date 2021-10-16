import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { NotificationService } from 'app/shared/notification/notification.service';
import { UserSettingsDirective } from 'app/shared/user-settings/user-settings.directive';
import { reloadNotificationSideBarMessage } from 'app/shared/notification/notification-sidebar/notification-sidebar.component';
import { UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { NotificationSetting } from 'app/shared/user-settings/notification-settings/notification-settings-structure';
import { UserSettingsService } from 'app/shared/user-settings/user-settings.service';
import { UserSettingsStructure } from 'app/shared/user-settings/user-settings.model';
import { AlertService } from 'app/core/util/alert.service';
import { NotificationSettingsService } from 'app/shared/user-settings/notification-settings/notification-settings.service';

@Component({
    selector: 'jhi-notification-settings',
    templateUrl: 'notification-settings.component.html',
    styleUrls: ['../user-settings.scss'],
})
export class NotificationSettingsComponent extends UserSettingsDirective implements OnInit {
    constructor(notificationService: NotificationService, userSettingsService: UserSettingsService, changeDetector: ChangeDetectorRef, alertService: AlertService) {
        super(userSettingsService, alertService, changeDetector);
    }

    userSettings: UserSettingsStructure<NotificationSetting>;
    settings: Array<NotificationSetting>;

    ngOnInit(): void {
        this.userSettingsCategory = UserSettingsCategory.NOTIFICATION_SETTINGS;
        this.changeEventMessage = reloadNotificationSideBarMessage;
        super.ngOnInit();
    }

    /**
     * Catches the toggle event from an user click
     * Toggles the respective setting and mark it as changed (only changed settings will be send to the server for saving)
     */
    toggleSetting(event: any, webApp: boolean) {
        this.settingsChanged = true;
        let settingId = event.currentTarget.id;
        // optionId String could have an appended (e.g.( " email" or " webapp" to specify which option to toggle
        if (settingId.indexOf(' ') !== -1) {
            settingId = settingId.substr(0, settingId.indexOf(' '));
        }
        const foundSetting = this.settings.find((setting) => setting.settingId === settingId);
        if (!foundSetting) {
            return;
        }
        if (webApp) {
            foundSetting!.webapp = !foundSetting!.webapp;
        } else {
            foundSetting!.email = !foundSetting!.email;
        }
        foundSetting.changed = true;
    }

    /**
     * Checks if the provided NotificationSetting has email support
     * @param setting which settingId will be checked
     */
    public checkIfNotificationSettingHasEmailSupport(setting: NotificationSetting): boolean {
        return !NotificationSettingsService.NOTIFICATION_SETTINGS_WITHOUT_EMAIL_SUPPORT.has(setting.settingId);
    }
}
