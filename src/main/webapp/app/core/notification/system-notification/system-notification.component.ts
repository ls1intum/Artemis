import { AfterViewChecked, Component, ElementRef, OnDestroy, OnInit, Renderer2, RendererStyleFlags2, inject } from '@angular/core';
import dayjs from 'dayjs/esm';
import { SystemNotification, SystemNotificationType } from 'app/core/shared/entities/system-notification.model';
import { AccountService } from 'app/core/auth/account.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { User } from 'app/core/user/user.model';
import { faExclamationTriangle, faInfoCircle, faTimes } from '@fortawesome/free-solid-svg-icons';
import { DOCUMENT, NgClass } from '@angular/common';
import { Subscription, filter } from 'rxjs';
import { convertDateFromServer } from 'app/shared/util/date.utils';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { SystemNotificationService } from 'app/core/notification/system-notification/system-notification.service';
import { LocalStorageService } from 'app/shared/service/local-storage.service';

export const WEBSOCKET_CHANNEL = '/topic/system-notification';
export const CLOSED_NOTIFICATION_IDS_STORAGE_KEY = 'system-notification.closedIds';

@Component({
    selector: 'jhi-system-notification',
    templateUrl: './system-notification.component.html',
    styleUrls: ['system-notification.scss'],
    imports: [NgClass, FaIconComponent],
})
export class SystemNotificationComponent implements OnInit, OnDestroy, AfterViewChecked {
    private accountService = inject(AccountService);
    private websocketService = inject(WebsocketService);
    private systemNotificationService = inject(SystemNotificationService);
    private localStorageService = inject(LocalStorageService);
    private renderer = inject(Renderer2);
    private elementRef = inject(ElementRef);
    private document = inject(DOCUMENT);

    readonly INFO = SystemNotificationType.INFO;
    readonly WARNING = SystemNotificationType.WARNING;

    notifications: SystemNotification[] = [];
    notificationsToDisplay: SystemNotification[] = [];
    closedIds: number[] = [];
    websocketStatusSubscription?: Subscription;
    systemNotificationSubscription?: Subscription;

    nextUpdateFuture?: ReturnType<typeof setTimeout>;
    private lastNotificationHeight = 0;

    // Icons
    faExclamationTriangle = faExclamationTriangle;
    faInfoCircle = faInfoCircle;
    faTimes = faTimes;

    private authSubscription?: Subscription;

    ngOnInit() {
        this.closedIds = this.localStorageService.retrieve<number[]>(CLOSED_NOTIFICATION_IDS_STORAGE_KEY) ?? [];
        this.loadActiveNotification();
        this.authSubscription = this.accountService.getAuthenticationState().subscribe((user: User | undefined) => {
            if (user) {
                setTimeout(() => {
                    this.websocketStatusSubscription = this.websocketService.connectionState.pipe(filter((status) => status.connected)).subscribe(() => this.subscribeSocket());
                }, 500);
            } else {
                this.websocketStatusSubscription?.unsubscribe();
            }
        });
    }

    ngAfterViewChecked() {
        this.updateNotificationHeightCssVariable();
    }

    ngOnDestroy() {
        if (this.nextUpdateFuture) {
            clearTimeout(this.nextUpdateFuture);
        }
        this.authSubscription?.unsubscribe();
        this.websocketStatusSubscription?.unsubscribe();
        this.systemNotificationSubscription?.unsubscribe();
        this.renderer.setStyle(this.document.documentElement, '--system-notification-height', '0px', RendererStyleFlags2.DashCase);
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
            this.pruneClosedIds();
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
        if (!this.closedIds.includes(notification.id!)) {
            this.closedIds.push(notification.id!);
        }
        this.localStorageService.store(CLOSED_NOTIFICATION_IDS_STORAGE_KEY, this.closedIds);
        this.selectVisibleNotificationsAndScheduleUpdate();
    }

    /**
     * Remove closed IDs that no longer correspond to any known notification,
     * so localStorage doesn't grow unboundedly.
     */
    private pruneClosedIds() {
        const knownIds = new Set(this.notifications.map((n) => n.id));
        this.closedIds = this.closedIds.filter((id) => knownIds.has(id));
        this.localStorageService.store(CLOSED_NOTIFICATION_IDS_STORAGE_KEY, this.closedIds);
    }

    private updateNotificationHeightCssVariable() {
        const height = (this.elementRef.nativeElement as HTMLElement).offsetHeight;
        if (height !== this.lastNotificationHeight) {
            this.lastNotificationHeight = height;
            this.renderer.setStyle(this.document.documentElement, '--system-notification-height', `${height}px`, RendererStyleFlags2.DashCase);
        }
    }
}
