import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import dayjs from 'dayjs/esm';
import { SystemNotification, SystemNotificationType } from 'app/core/shared/entities/system-notification.model';
import { AccountService } from 'app/core/auth/account.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { User } from 'app/core/user/user.model';
import { faExclamationTriangle, faInfoCircle, faTimes } from '@fortawesome/free-solid-svg-icons';
import { Subscription, filter } from 'rxjs';
import { convertDateFromServer } from 'app/shared/util/date.utils';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { SystemNotificationService } from 'app/core/notification/system-notification/system-notification.service';

export const WEBSOCKET_CHANNEL = '/topic/system-notification';

@Component({
    selector: 'jhi-system-notification',
    templateUrl: './system-notification.component.html',
    styleUrls: ['system-notification.scss'],
    imports: [NgClass, FaIconComponent],
})
export class SystemNotificationComponent implements OnInit, OnDestroy {
    private accountService = inject(AccountService);
    private websocketService = inject(WebsocketService);
    private systemNotificationService = inject(SystemNotificationService);

    readonly INFO = SystemNotificationType.INFO;
    readonly WARNING = SystemNotificationType.WARNING;

    notifications: SystemNotification[] = [];
    notificationsToDisplay: SystemNotification[] = [];
    closedIds: number[] = [];
    websocketStatusSubscription?: Subscription;
    systemNotificationSubscription?: Subscription;

    nextUpdateFuture?: ReturnType<typeof setTimeout>;

    // Icons
    faExclamationTriangle = faExclamationTriangle;
    faInfoCircle = faInfoCircle;
    faTimes = faTimes;

    ngOnInit() {
        this.loadActiveNotification();
        this.accountService.getAuthenticationState().subscribe((user: User | undefined) => {
            if (user) {
                setTimeout(() => {
                    this.websocketStatusSubscription = this.websocketService.connectionState.pipe(filter((status) => status.connected)).subscribe(() => this.subscribeSocket());
                }, 500);
            } else {
                this.websocketStatusSubscription?.unsubscribe();
            }
        });
    }

    ngOnDestroy() {
        this.websocketStatusSubscription?.unsubscribe();
        this.systemNotificationSubscription?.unsubscribe();
    }

    private loadActiveNotification() {
        this.systemNotificationService.getActiveNotifications().subscribe((notifications: SystemNotification[]) => {
            this.notifications = notifications;
            this.selectVisibleNotificationsAndScheduleUpdate();
        });
    }

    /**
     * Listens to updates of the system notification array on the websocket.
     * The server submits the entire list of relevant system notifications if they are updated
     */
    private subscribeSocket() {
        this.systemNotificationSubscription?.unsubscribe();
        this.systemNotificationSubscription = this.websocketService.subscribe<SystemNotification[]>(WEBSOCKET_CHANNEL).subscribe((notifications: SystemNotification[]) => {
            notifications.forEach((notification) => {
                notification.notificationDate = convertDateFromServer(notification.notificationDate);
                notification.expireDate = convertDateFromServer(notification.expireDate);
            });
            this.notifications = notifications;
            this.closedIds = [];
            this.selectVisibleNotificationsAndScheduleUpdate();
        });
    }

    /**
     * Schedule a change detection cycle of this component at the next date that changes
     */
    private selectVisibleNotificationsAndScheduleUpdate() {
        const now = dayjs();
        this.notificationsToDisplay = this.notifications
            .filter((notification) => !this.closedIds.includes(notification.id!))
            .filter((notification) => notification.notificationDate?.isSameOrBefore(now) && (notification.expireDate?.isAfter(now) ?? true));

        if (this.nextUpdateFuture) {
            clearTimeout(this.nextUpdateFuture);
            this.nextUpdateFuture = undefined;
        }

        const nextRelevantTimestamp = this.notifications
            .flatMap((notification) => [notification.expireDate, notification.notificationDate])
            .filter((date) => date?.isAfter(now))
            .map((date) => date!)
            .reduce((previous, current) => (previous ? dayjs.min(previous, current) : current), undefined);

        if (nextRelevantTimestamp) {
            this.nextUpdateFuture = setTimeout(() => {
                this.selectVisibleNotificationsAndScheduleUpdate();
            }, nextRelevantTimestamp.diff(now));
        }
    }

    close(notification: SystemNotification) {
        this.closedIds.push(notification.id!);
        this.selectVisibleNotificationsAndScheduleUpdate();
    }
}
