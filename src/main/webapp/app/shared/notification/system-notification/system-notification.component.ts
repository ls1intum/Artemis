import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import dayjs from 'dayjs';
import { SystemNotification, SystemNotificationType } from 'app/entities/system-notification.model';
import { AccountService } from 'app/core/auth/account.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { User } from 'app/core/user/user.model';
import { SystemNotificationService } from 'app/shared/notification/system-notification/system-notification.service';
import { IconProp } from '@fortawesome/fontawesome-svg-core';

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

    systemNotificationTimers: Map<SystemNotification, ReturnType<typeof setTimeout>> = new Map<SystemNotification, ReturnType<typeof setTimeout>>();

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
        this.systemNotificationService.currentSystemNotificationUpdate.subscribe((event) => {
            if (event) {
                if (this.notification) {
                    this.setTimedCheck(this.notification);
                } else {
                    this.loadActiveNotification();
                }
            }
        });
    }

    /**
     * set asynchronous timeout to update/check/hide current system notification
     * @param notification which expiration date is used as the update time
     */
    private setTimedCheck(notification: SystemNotification) {
        // check if this notification already has a timer
        // if true then remove this timer -> it is outdated
        if (this.systemNotificationTimers.has(notification)) {
            clearTimeout(this.systemNotificationTimers.get(notification)!);
        }
        if (notification.expireDate) {
            const timeoutReference = setTimeout(() => {
                // remove timer from timer map
                this.systemNotificationTimers.delete(notification);
                this.loadActiveNotification();
            }, 1000 + notification.expireDate.diff(dayjs(), 'ms'));
            this.systemNotificationTimers.set(notification, timeoutReference);
        }
    }

    private loadActiveNotification() {
        this.systemNotificationService.getActiveNotification().subscribe((notification: SystemNotification) => {
            if (notification) {
                this.notification = notification;
                this.setAlertClass();
                this.setAlertIcon();
                this.setTimedCheck(notification);
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
                this.alertIcon = 'exclamation-triangle';
            } else {
                this.alertIcon = 'info-circle';
            }
        }
    }
}
