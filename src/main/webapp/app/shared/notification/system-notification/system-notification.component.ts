import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import dayjs from 'dayjs/esm';
import { SystemNotification, SystemNotificationType } from 'app/entities/system-notification.model';
import { AccountService } from 'app/core/auth/account.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { User } from 'app/core/user/user.model';
import { SystemNotificationService } from 'app/shared/notification/system-notification/system-notification.service';
import { faExclamationTriangle, faInfoCircle, faTimes } from '@fortawesome/free-solid-svg-icons';
import { filter, Subscription } from 'rxjs';
import { convertDateFromServer } from 'app/utils/date.utils';

export const WEBSOCKET_CHANNEL = '/topic/system-notification';

@Component({
    selector: 'jhi-system-notification',
    templateUrl: './system-notification.component.html',
    styleUrls: ['system-notification.scss'],
})
export class SystemNotificationComponent implements OnInit, OnDestroy {
    readonly INFO = SystemNotificationType.INFO;
    readonly WARNING = SystemNotificationType.WARNING;

    notifications: SystemNotification[] = [];
    notificationsToDisplay: SystemNotification[] = [];
    closedIds: number[] = [];
    websocketStatusSubscription?: Subscription;

    nextUpdateFuture?: ReturnType<typeof setTimeout>;

    // Icons
    faExclamationTriangle = faExclamationTriangle;
    faInfoCircle = faInfoCircle;
    faTimes = faTimes;

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
                setTimeout(() => {
                    this.websocketStatusSubscription = this.jhiWebsocketService.connectionState.pipe(filter((status) => status.connected)).subscribe(() => this.subscribeSocket());
                }, 500);
            } else {
                this.websocketStatusSubscription?.unsubscribe();
            }
        });
    }

    ngOnDestroy() {
        this.websocketStatusSubscription?.unsubscribe();
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
     * @private
     */
    private subscribeSocket() {
        this.jhiWebsocketService.subscribe(WEBSOCKET_CHANNEL);
        this.jhiWebsocketService.receive(WEBSOCKET_CHANNEL).subscribe((notifications: SystemNotification[]) => {
            notifications.forEach((notification) => {
                notification.notificationDate = convertDateFromServer(notification.notificationDate);
                notification.expireDate = convertDateFromServer(notification.expireDate);
            });
            this.notifications = notifications;
            this.selectVisibleNotificationsAndScheduleUpdate();
        });
    }

    /**
     * Schedule a change detection cycle of this component at the next date that changes
     * @private
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
