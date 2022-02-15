import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import dayjs from 'dayjs/esm';
import { SystemNotification, SystemNotificationType } from 'app/entities/system-notification.model';
import { AccountService } from 'app/core/auth/account.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { User } from 'app/core/user/user.model';
import { SystemNotificationService } from 'app/shared/notification/system-notification/system-notification.service';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faExclamationTriangle, faInfoCircle } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-system-notification',
    templateUrl: './system-notification.component.html',
    styleUrls: ['system-notification.scss'],
})
export class SystemNotificationComponent implements OnInit {
    readonly INFO = SystemNotificationType.INFO;
    readonly WARNING = SystemNotificationType.WARNING;
    notification: SystemNotification | undefined;
    alertClass: string;
    alertIcon: IconProp;
    websocketChannel: string;

    constructor(
        private route: ActivatedRoute,
        private accountService: AccountService,
        private jhiWebsocketService: JhiWebsocketService,
        private systemNotificationService: SystemNotificationService,
    ) {}

    ngOnInit() {
        this.loadActiveNotification();
        this.accountService.getAuthenticationState().subscribe((user: User | undefined) => {
            if (user) {
                // maybe use connectedPromise as a set function
                setTimeout(() => {
                    this.jhiWebsocketService.bind('connect', () => {
                        this.subscribeSocket();
                    });
                }, 500);
            }
        });
    }

    private loadActiveNotification() {
        this.systemNotificationService.getActiveNotification().subscribe((notification: SystemNotification) => {
            if (notification) {
                this.notification = notification;
                this.setAlertClass();
                this.setAlertIcon();
            } else {
                this.notification = undefined;
            }
        });
    }

    private subscribeSocket() {
        this.websocketChannel = '/topic/system-notification';
        this.jhiWebsocketService.subscribe(this.websocketChannel);
        this.jhiWebsocketService.receive(this.websocketChannel).subscribe((systemNotification: SystemNotification | string) => {
            // as we cannot send null as websocket payload (this is not supported), we send a string 'deleted' in case the system notification was deleted
            if (systemNotification === 'deleted') {
                this.loadActiveNotification();
                return;
            }
            systemNotification = systemNotification as SystemNotification;
            systemNotification.notificationDate = systemNotification.notificationDate ? dayjs(systemNotification.notificationDate) : undefined;
            systemNotification.expireDate = systemNotification.expireDate ? dayjs(systemNotification.expireDate) : undefined;
            if (!this.notification) {
                this.checkNotificationDates(systemNotification);
            } else {
                if (this.notification.id === systemNotification.id) {
                    this.checkNotificationDates(systemNotification);
                } else if (systemNotification.notificationDate!.isBefore(this.notification.notificationDate!) && systemNotification.expireDate!.isAfter(dayjs())) {
                    this.checkNotificationDates(systemNotification);
                }
            }
        });
    }

    private checkNotificationDates(systemNotification: SystemNotification) {
        if (systemNotification.expireDate!.isAfter(dayjs()) && systemNotification.notificationDate!.isBefore(dayjs())) {
            this.notification = systemNotification;
            this.setAlertClass();
            this.setAlertIcon();
        } else {
            this.loadActiveNotification();
            return;
        }
    }

    private setAlertClass(): void {
        if (this.notification) {
            if (this.notification.type === SystemNotificationType.WARNING) {
                this.alertClass = 'alert-warning';
            } else {
                this.alertClass = 'alert-info';
            }
        }
    }

    private setAlertIcon(): void {
        if (this.notification) {
            if (this.notification.type === SystemNotificationType.WARNING) {
                this.alertIcon = faExclamationTriangle;
            } else {
                this.alertIcon = faInfoCircle;
            }
        }
    }
}
