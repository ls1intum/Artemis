import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { NotificationService } from 'app/shared/notification/notification.service';
import { UserSettingsPrototypeComponent } from 'app/shared/user-settings/user-settings-prototype/user-settings-prototype.component';
import { JhiAlertService } from 'ng-jhipster';
import { reloadNotificationSideBarMessage } from 'app/shared/notification/notification-sidebar/notification-sidebar.component';
import { UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { NotificationOptionCore } from 'app/shared/user-settings/notification-settings/notification-settings.default';
import { UserSettingsService } from 'app/shared/user-settings/user-settings.service';
import { UserSettings } from 'app/shared/user-settings/user-settings.model';

@Component({
    selector: 'jhi-notification-settings',
    templateUrl: 'notification-settings.component.html',
    styleUrls: ['../user-settings-prototype/user-settings-prototype.component.scss'],
})
export class NotificationSettingsComponent extends UserSettingsPrototypeComponent implements OnInit {
    constructor(notificationService: NotificationService, userSettingsService: UserSettingsService, changeDetector: ChangeDetectorRef, alertService: JhiAlertService) {
        super(userSettingsService, alertService, changeDetector);
    }

    userSettings: UserSettings<NotificationOptionCore>;
    optionCores: Array<NotificationOptionCore>;

    ngOnInit(): void {
        this.userSettingsCategory = UserSettingsCategory.NOTIFICATION_SETTINGS;
        this.changeEventMessage = reloadNotificationSideBarMessage;
        super.ngOnInit();
    }

    /**
     * Catches the toggle event from an user click
     * Toggles the respective option and mark it as changed (only changed option cores will be send to the server for saving)
     */
    toggleOption(event: any, webApp: boolean) {
        this.optionsChanged = true;
        let optionId = event.currentTarget.id;
        optionId = optionId.substr(0, optionId.indexOf(' '));
        const foundOptionCore = this.optionCores.find((core) => core.optionSpecifier === optionId);
        if (!foundOptionCore) {
            return;
        }
        if (webApp) {
            foundOptionCore!.webapp = !foundOptionCore!.webapp;
        } else {
            foundOptionCore!.email = !foundOptionCore!.email;
        }
        foundOptionCore.changed = true;
    }
}
