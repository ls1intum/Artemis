import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { UserService } from 'app/core';
import {
    SystemNotification,
    SystemNotificationService,
    SystemNotificationType
} from 'app/entities/system-notification';

@Component({
    selector: 'jhi-notification-mgmt-update',
    templateUrl: './notification-management-update.component.html'
})
export class NotificationMgmtUpdateComponent implements OnInit {
    notification: SystemNotification;
    isSaving: boolean;

    systemNotificationTypes = [
        {name: 'INFO', value: SystemNotificationType.INFO},
        {name: 'WARNING', value: SystemNotificationType.WARNING}
    ];

    constructor(
        private userService: UserService,
        private systemNotificationService: SystemNotificationService,
        private route: ActivatedRoute,
    ) {
    }

    ngOnInit() {
        this.isSaving = false;
        this.route.data.subscribe(({notification}) => {
            this.notification = notification.body ? notification.body : notification;
        });
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.notification.id) {
            this.systemNotificationService.update(this.notification).subscribe(response => this.onSaveSuccess(response.body), () => this.onSaveError());
        } else {
            this.systemNotificationService.create(this.notification).subscribe(response => this.onSaveSuccess(response.body), () => this.onSaveError());
        }
    }

    private onSaveSuccess(result: SystemNotification) {
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError() {
        this.isSaving = false;
    }
}
