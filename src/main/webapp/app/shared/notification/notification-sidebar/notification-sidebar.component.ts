import { Component, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { User } from 'app/core/user/user.model';
import { UserService } from 'app/core/user/user.service';
import * as moment from 'moment';
import { Moment } from 'moment';
import { GroupNotification } from 'app/entities/group-notification.model';
import { Notification } from 'app/entities/notification.model';
import { AccountService } from 'app/core/auth/account.service';
import { NotificationService } from 'app/shared/notification/notification.service';

@Component({
    selector: 'jhi-notification-sidebar',
    templateUrl: './notification-sidebar.component.html',
    styleUrls: ['./notification-sidebar.scss'],
})
export class NotificationSidebarComponent implements OnInit {
    showSidebar = false;
    notifications: Notification[] = [];
    sortedNotifications: Notification[] = [];
    recentNotificationCount = 0;
    totalNotifications = 0;
    lastNotificationRead: Moment | null = null;
    page = 0;
    notificationsPerPage = 25;
    error: string | null = null;

    constructor(private notificationService: NotificationService, private userService: UserService, private accountService: AccountService) {}

    /**
     * Load notifications when user is authenticated on component initialization.
     */
    ngOnInit(): void {
        this.accountService.getAuthenticationState().subscribe((user: User | null) => {
            if (user) {
                if (user.lastNotificationRead) {
                    this.lastNotificationRead = user.lastNotificationRead;
                }
                this.loadNotifications();
            }
        });
    }

    /**
     * Show the sidebar when it is not visible and hide the sidebar when it is visible.
     */
    toggleSidebar(): void {
        this.showSidebar = !this.showSidebar;
    }

    /**
     * Will be executed when a notification was clicked. The notification sidebar will be closed and the actual interpretation
     * of what should happen after the notification was clicked will be handled in the notification service.
     * @param notification that will be interpreted of type {Notification}
     */
    startNotification(notification: Notification): void {
        this.showSidebar = false;
        this.notificationService.interpretNotification(notification as GroupNotification);
    }

    /**
     * Update the user's lastNotificationRead setting. As this method will be executed when the user opens the sidebar, the
     * component's lastNotificationRead attribute will be updated only after two seconds so that the notification `new` badges
     * won't disappear immediately.
     */
    updateLastNotificationRead(): void {
        this.userService.updateLastNotificationRead().subscribe(() => {
            const lastNotificationReadNow = moment();
            setTimeout(() => {
                this.lastNotificationRead = lastNotificationReadNow;
            }, 2000);
        });
    }

    /**
     * TODO
     */
    onScroll(): void {
        // TODO: will be called to often --> fix, increase threshold again to 350
        const container = document.getElementById('notificationSidebarContainer');
        const threshold = 50;
        if (container) {
            const height = container.scrollHeight - container.offsetHeight;
            if (height > threshold && container.scrollTop > height - threshold) {
                console.log('Load more');
            }
        }
    }

    private loadNotifications(): void {
        // Query recent and first batch of non-recent notifications.
        this.notificationService
            .query({
                page: this.page - 1,
                size: this.notificationsPerPage,
                sort: ['notificationDate,desc'],
            })
            .subscribe(
                (res: HttpResponse<Notification[]>) => this.onSuccess(res.body!, res.headers),
                (res: HttpErrorResponse) => (this.error = res.message),
            );
        // Subscribe to notification updates that are sent via websocket.
        setTimeout(() => {
            this.notificationService.subscribeUserNotifications();
        }, 500);
        this.notificationService.subscribeToSocketMessages().subscribe((notification: Notification) => {
            // TODO: How can it happen that the same id comes twice through the channel?
            if (notification && !this.notifications.some(({ id }) => id === notification.id)) {
                notification.notificationDate = notification.notificationDate ? moment(notification.notificationDate) : null;
                this.notifications.push(notification);
                this.updateNotifications();
            }
        });
    }

    private onSuccess(notifications: Notification[], headers: HttpHeaders) {
        this.totalNotifications = Number(headers.get('X-Total-Count')!);
        this.notifications = notifications;
        this.updateNotifications();
    }

    private updateNotifications(): void {
        this.sortedNotifications = this.notifications.sort((a: Notification, b: Notification) => {
            return moment(b.notificationDate!).valueOf() - moment(a.notificationDate!).valueOf();
        });
        this.updateRecentNotificationCount();
    }

    private updateRecentNotificationCount(): void {
        if (!this.notifications) {
            this.recentNotificationCount = 0;
        } else if (this.lastNotificationRead) {
            this.recentNotificationCount = this.notifications.filter((notification) => {
                return notification.notificationDate && notification.notificationDate.isAfter(this.lastNotificationRead!);
            }).length;
        } else {
            this.recentNotificationCount = this.notifications.length;
        }
    }
}
