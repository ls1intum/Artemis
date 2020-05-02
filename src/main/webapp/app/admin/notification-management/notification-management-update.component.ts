import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { UserService } from 'app/core/user/user.service';
import { SystemNotification, SystemNotificationType } from 'app/entities/system-notification.model';
import { SystemNotificationService } from 'app/core/system-notification/system-notification.service';

@Component({
    selector: 'jhi-notification-mgmt-update',
    templateUrl: './notification-management-update.component.html',
})
export class NotificationMgmtUpdateComponent implements OnInit {
    notification: SystemNotification;
    isSaving: boolean;

    systemNotificationTypes = [
        { name: 'INFO', value: SystemNotificationType.INFO },
        { name: 'WARNING', value: SystemNotificationType.WARNING },
    ];

    constructor(private userService: UserService, private systemNotificationService: SystemNotificationService, private route: ActivatedRoute) {}

    /**
     * Loads notification from route data
     */
    ngOnInit() {
        this.isSaving = false;
        this.route.data.subscribe(({ notification }) => {
            this.notification = notification.body ? notification.body : notification;
        });
    }

    /**
     * Returns to previous state (same as back button in the browser)
     */
    previousState() {
        window.history.back();
    }

    /**
     * Either creates or updates the notification, when the form is submitted
     */
    save() {
        this.isSaving = true;
        if (this.notification.id) {
            this.systemNotificationService.update(this.notification).subscribe(
                () => this.onSaveSuccess(),
                () => this.onSaveError(),
            );
        } else {
            this.systemNotificationService.create(this.notification).subscribe(
                () => this.onSaveSuccess(),
                () => this.onSaveError(),
            );
        }
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError() {
        this.isSaving = false;
    }
}
