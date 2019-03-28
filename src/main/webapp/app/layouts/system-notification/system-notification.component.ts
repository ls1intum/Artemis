import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import * as moment from 'moment';
import { Notification } from 'app/entities/notification';
import {
    SystemNotification,
    SystemNotificationService,
    SystemNotificationType
} from 'app/entities/system-notification';
import { HttpResponse } from '@angular/common/http';
import { JhiWebsocketService } from 'app/core';

@Component({
    selector: 'jhi-system-notification',
    templateUrl: './system-notification.component.html'
})
export class SystemNotificationComponent implements OnInit {
    readonly INFO = SystemNotificationType.INFO;
    readonly WARNING = SystemNotificationType.WARNING;
    notification: SystemNotification;
    alertClass: string;
    alertIcon: string;
    websocketChannel: string;

    constructor(
        private route: ActivatedRoute,
        private jhiWebsocketService: JhiWebsocketService,
        private systemNotificationService: SystemNotificationService
    ) {
    }

    ngOnInit() {
        this.loadActiveNotification();
        // maybe use connectedPromise as a set function
        setTimeout(() => {
            this.jhiWebsocketService.bind('connect', () => {
                this.subscribeSocket();
            });
        }, 500);

    }

    loadActiveNotification() {
        this.systemNotificationService.getActiveNotification().subscribe((res: HttpResponse<SystemNotification>) => {
            this.notification = res.body;
            this.setAlertClass();
            this.setAlertIcon();
        });
    }

    subscribeSocket() {
        this.websocketChannel = '/topic/system-notification';
        this.jhiWebsocketService.subscribe(this.websocketChannel);
        this.jhiWebsocketService.receive(this.websocketChannel).subscribe((systemNotification: SystemNotification) => {
            if (!systemNotification) {
                this.loadActiveNotification();
                return;
            }
            systemNotification.notificationDate = systemNotification.notificationDate ? moment(systemNotification.notificationDate) : null;
            systemNotification.expireDate = systemNotification.expireDate ? moment(systemNotification.expireDate) : null;
            if (!this.notification) {
                this.checkNotificationDates(systemNotification)
            } else {
                if (this.notification.id === systemNotification.id) {
                    this.checkNotificationDates(systemNotification)
                } else if (systemNotification.notificationDate.isBefore(this.notification.notificationDate) && systemNotification.expireDate.isAfter(moment())) {
                    this.checkNotificationDates(systemNotification)
                }
            }
        });

    }

    checkNotificationDates(systemNotification: SystemNotification) {
        if (systemNotification.expireDate.isAfter(moment()) && systemNotification.notificationDate.isBefore(moment())) {
            this.notification = systemNotification;
            this.setAlertClass();
            this.setAlertIcon();
        } else {
            this.loadActiveNotification();
            return;
        }
    }

    setAlertClass(): void {
        if (this.notification.type === SystemNotificationType.WARNING) {
            this.alertClass = 'alert-warning';
        } else {
            this.alertClass = 'alert-info';
        }
    }

    setAlertIcon(): void {
        if (this.notification.type === SystemNotificationType.WARNING) {
            this.alertIcon = 'exclamation-triangle';
        } else {
            this.alertIcon = 'info-circle';
        }
    }
}
