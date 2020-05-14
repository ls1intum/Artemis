import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import * as moment from 'moment';
import { SystemNotification, SystemNotificationType } from 'app/entities/system-notification.model';
import { AccountService } from 'app/core/auth/account.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { User } from 'app/core/user/user.model';
import { SystemNotificationService } from 'app/core/system-notification/system-notification.service';

@Component({
    selector: 'jhi-system-notification',
    templateUrl: './system-notification.component.html',
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
        private accountService: AccountService,
        private jhiWebsocketService: JhiWebsocketService,
        private systemNotificationService: SystemNotificationService,
    ) {}

    /**
     * Lifecycle function which is called on initialisations. Calls {@link loadActiveNotification} when a user is connected.
     */
    ngOnInit() {
        this.accountService.getAuthenticationState().subscribe((user: User | null) => {
            this.loadActiveNotification();
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

    /** Subscribes to {@link systemNotificationService~getActiveNotification}.
     * It sets the {@link notification} and triggers {@link setAlertClass} & {@link setAlertIcon} when a notification is received.
     * @method
     */
    loadActiveNotification() {
        this.systemNotificationService.getActiveNotification().subscribe((notification: SystemNotification) => {
            this.notification = notification;
            this.setAlertClass();
            this.setAlertIcon();
        });
    }

    /** Sets the websocket channel and subscribes to it for receiving system notifications. See {@link jhiWebsocketService~subscribe}, {@link jhiWebsocketService~receive}.
     * Before displaying a notification that is not null, the {@link SystemNotification~id} and {@link SystemNotification~notificationDate} are validated.
     * {@link checkNotificationDates}.
     * @method subscribeSocket
     */
    subscribeSocket() {
        this.websocketChannel = '/topic/system-notification';
        this.jhiWebsocketService.subscribe(this.websocketChannel);
        this.jhiWebsocketService.receive(this.websocketChannel).subscribe((systemNotification: SystemNotification | string) => {
            // as we cannot send null as websocket payload (this is not supported), we send a string 'deleted' in case the system notification was deleted
            if (systemNotification === 'deleted') {
                this.loadActiveNotification();
                return;
            }
            systemNotification = systemNotification as SystemNotification;
            systemNotification.notificationDate = systemNotification.notificationDate ? moment(systemNotification.notificationDate) : null;
            systemNotification.expireDate = systemNotification.expireDate ? moment(systemNotification.expireDate) : null;
            if (!this.notification) {
                this.checkNotificationDates(systemNotification);
            } else {
                if (this.notification.id === systemNotification.id) {
                    this.checkNotificationDates(systemNotification);
                } else if (systemNotification.notificationDate!.isBefore(this.notification.notificationDate!) && systemNotification.expireDate!.isAfter(moment())) {
                    this.checkNotificationDates(systemNotification);
                }
            }
        });
    }

    /** Checks whether the notifications are still valid before triggering {@link setAlertClass} and {@link setAlertIcon} and setting it in {@link notification}.
     * If they are not valid, {@link loadActiveNotification} is called.
     * @method
     * @param systemNotification {SystemNotification}
     */
    checkNotificationDates(systemNotification: SystemNotification) {
        if (systemNotification.expireDate!.isAfter(moment()) && systemNotification.notificationDate!.isBefore(moment())) {
            this.notification = systemNotification;
            this.setAlertClass();
            this.setAlertIcon();
        } else {
            this.loadActiveNotification();
            return;
        }
    }

    /** Sets the {@link alertClass} according to the {@link SystemNotificationType}.
     * For {@link SystemNotificationType~WARNING} {@link alertClass} is set to 'alert-warning', otherwise it is set to 'alert-info'.
     * @method
     */
    setAlertClass(): void {
        if (this.notification) {
            if (this.notification.type === SystemNotificationType.WARNING) {
                this.alertClass = 'alert-warning';
            } else {
                this.alertClass = 'alert-info';
            }
        }
    }

    /** Sets the {@link alertIcon} according to the {@link SystemNotificationType}.
     * For {@link SystemNotificationType~WARNING} {@link alertIcon} is set to 'exclamation-triangle', otherwise it is set to 'info-circle'.
     * @method
     */
    setAlertIcon(): void {
        if (this.notification) {
            if (this.notification.type === SystemNotificationType.WARNING) {
                this.alertIcon = 'exclamation-triangle';
            } else {
                this.alertIcon = 'info-circle';
            }
        }
    }
}
