import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { NotificationService } from 'app/shared/notification/notification.service';
import { UserSettingsDirective } from 'app/shared/user-settings/user-settings.directive';
import { JhiAlertService } from 'ng-jhipster';
import { reloadNotificationSideBarMessage } from 'app/shared/notification/notification-sidebar/notification-sidebar.component';
import { UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { NotificationSetting } from 'app/shared/user-settings/notification-settings/notification-settings-structure';
import { UserSettingsService } from 'app/shared/user-settings/user-settings.service';
import { UserSettingsStructure } from 'app/shared/user-settings/user-settings.model';

@Component({
    selector: 'jhi-notification-settings',
    templateUrl: 'notification-settings.component.html',
    styleUrls: ['../user-settings.scss'],
})
export class NotificationSettingsComponent extends UserSettingsDirective implements OnInit {
    constructor(notificationService: NotificationService, userSettingsService: UserSettingsService, changeDetector: ChangeDetectorRef, alertService: JhiAlertService) {
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
    toggleSetting(event: any) {
        this.settingsChanged = true;
        const settingId = event.currentTarget.id;
        const foundSetting = this.settings.find((setting) => setting.settingId === settingId);
        if (!foundSetting) {
            return;
        }
        foundSetting!.webapp = !foundSetting!.webapp;
        foundSetting.changed = true;
    }
}
