import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { NotificationService } from 'app/shared/notification/notification.service';
import { UserSettingsDirective } from 'app/shared/user-settings/user-settings.directive';
import { reloadNotificationSideBarMessage } from 'app/shared/notification/notification-sidebar/notification-sidebar.component';
import { UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { NotificationSetting } from 'app/shared/user-settings/notification-settings/notification-settings-structure';
import { UserSettingsService } from 'app/shared/user-settings/user-settings.service';
import { UserSettingsStructure } from 'app/shared/user-settings/user-settings.model';
import { AlertService } from 'app/core/util/alert.service';
import { faInfoCircle, faSave } from '@fortawesome/free-solid-svg-icons';
import { NotificationSettingsService } from 'app/shared/user-settings/notification-settings/notification-settings.service';

export enum NotificationSettingsCommunicationChannel {
    WEBAPP,
    EMAIL,
}

@Component({
    selector: 'jhi-notification-settings',
    templateUrl: 'notification-settings.component.html',
    styleUrls: ['../user-settings.scss'],
})
export class NotificationSettingsComponent extends UserSettingsDirective implements OnInit {
    // Icons
    faSave = faSave;
    faInfoCircle = faInfoCircle;

    constructor(
        notificationService: NotificationService,
        userSettingsService: UserSettingsService,
        changeDetector: ChangeDetectorRef,
        alertService: AlertService,
        private notificationSettingsService: NotificationSettingsService,
    ) {
        super(userSettingsService, alertService, changeDetector);
    }

    userSettings: UserSettingsStructure<NotificationSetting>;
    settings: Array<NotificationSetting>;

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
