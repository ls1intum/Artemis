import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { NotificationService } from 'app/shared/notification/notification.service';
import { UserSettingsService } from 'app/shared/user-settings/user-settings.service';
import { UserSettingsPrototypeComponent } from 'app/shared/user-settings/user-settings-prototype/user-settings-prototype.component';
import { defaultNotificationSettings } from 'app/shared/user-settings/notification-settings/notification-settings.default';

@Component({
    selector: 'jhi-notification-settings',
    templateUrl: '../user-settings-prototype/user-settings-prototype.component.html',
    styleUrls: ['../user-settings-prototype/user-settings-prototype.component.scss'],
})
export class NotificationSettingsComponent extends UserSettingsPrototypeComponent implements OnInit {
    userSettingsCategory = defaultNotificationSettings.category;

    constructor(notificationService: NotificationService, userSettingsService: UserSettingsService, changeDetector: ChangeDetectorRef) {
        super(notificationService, userSettingsService, changeDetector);
    }

    ngOnInit(): void {
        super.ngOnInit();
    }
}
