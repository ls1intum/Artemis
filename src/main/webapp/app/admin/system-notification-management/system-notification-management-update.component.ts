import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { faBan, faSave } from '@fortawesome/free-solid-svg-icons';
import { UserService } from 'app/core/user/user.service';
import { SystemNotification, SystemNotificationType } from 'app/entities/system-notification.model';
import { SystemNotificationService } from 'app/shared/notification/system-notification/system-notification.service';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';

@Component({
    selector: 'jhi-system-notification-management-update',
    templateUrl: './system-notification-management-update.component.html',
})
export class SystemNotificationManagementUpdateComponent implements OnInit {
    notification: SystemNotification;
    isSaving: boolean;

    systemNotificationTypes = [
        { name: 'INFO', value: SystemNotificationType.INFO },
        { name: 'WARNING', value: SystemNotificationType.WARNING },
    ];

    // Icons
    faSave = faSave;
    faBan = faBan;

    constructor(
        private userService: UserService,
        private systemNotificationService: SystemNotificationService,
        private route: ActivatedRoute,
        private navigationUtilService: ArtemisNavigationUtilService,
    ) {}

    /**
     * Loads notification from route data
     */
    ngOnInit() {
        this.isSaving = false;
        // create a new notification, and only overwrite it if we fetch a notification to edit
        this.notification = new SystemNotification();
        this.route.parent!.data.subscribe(({ notification }) => {
            if (notification) {
                this.notification = notification.body ? notification.body : notification;
            }
        });
    }

    /**
     * Returns to previous state (same as back button in the browser)
     * Returns to the detail page if there is no previous state and we edited an existing notification
     * Returns to the overview page if there is no previous state and we created a new notification
     */
    previousState() {
        // Newly created notifications don't have an id yet
        this.navigationUtilService.navigateBackWithOptional(['admin', 'system-notification-management'], this.notification.id?.toString());
    }

    /**
     * Either creates or updates the notification, when the form is submitted
     */
    save() {
        this.isSaving = true;
        if (this.notification.id) {
            this.systemNotificationService.update(this.notification).subscribe({
                next: () => this.onSaveSuccess(),
                error: () => this.onSaveError(),
            });
        } else {
            this.systemNotificationService.create(this.notification).subscribe({
                next: () => this.onSaveSuccess(),
                error: () => this.onSaveError(),
            });
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
