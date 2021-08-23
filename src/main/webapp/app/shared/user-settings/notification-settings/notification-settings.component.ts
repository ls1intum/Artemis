import { ChangeDetectorRef, Component, OnInit, TemplateRef } from '@angular/core';
import { NotificationService } from 'app/shared/notification/notification.service';
import { UserSettingsService } from 'app/shared/user-settings/user-settings.service';
import { UserSettingsPrototypeComponent } from 'app/shared/user-settings/user-settings-prototype/user-settings-prototype.component';
import { defaultNotificationSettings } from 'app/shared/user-settings/notification-settings/notification-settings.default';
import { JhiAlertService } from 'ng-jhipster';
import { reloadNotificationSideBarMessage } from 'app/shared/notification/notification-sidebar/notification-sidebar.component';
import { AccountService } from 'app/core/auth/account.service';

@Component({
    selector: 'jhi-notification-settings',
    templateUrl: '../user-settings-prototype/user-settings-prototype.component.html',
    styleUrls: ['../user-settings-prototype/user-settings-prototype.component.scss'],
})
export class NotificationSettingsComponent extends UserSettingsPrototypeComponent implements OnInit {
    constructor(notificationService: NotificationService, userSettingsService: UserSettingsService, changeDetector: ChangeDetectorRef, alertService: JhiAlertService) {
        super(notificationService, userSettingsService, alertService, changeDetector);
    }

    ngOnInit(): void {
        super.ngOnInit();
        this.userSettingsCategory = defaultNotificationSettings.category;
        this.changeEventMessage = reloadNotificationSideBarMessage;
    }
}
