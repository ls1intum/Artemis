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
    showOptions = false;
    loading = false;
    notifications: Notification[] = [];
    sortedNotifications: Notification[] = [];
    recentNotificationCount = 0;
    totalNotifications = 0;
    lastNotificationRead?: Moment;
    page = 0;
    notificationsPerPage = 25;
    error?: string;

    constructor(private notificationService: NotificationService, private userService: UserService, private accountService: AccountService) {}

    /**
     * Load notifications when user is authenticated on component initialization.
     */
    ngOnInit(): void {
        this.accountService.getAuthenticationState().subscribe((user: User | undefined) => {
            if (user) {
                if (user.lastNotificationRead) {
                    this.lastNotificationRead = user.lastNotificationRead;
                }
                //this.loadNotificationOptions(); TODO only explicitly call in notification-settings component if it is open, the server has to filter the notifications already on its own
                this.loadNotifications();
                this.subscribeToNotificationUpdates();
            }
        });
    }

    /**
     * Show the sidebar when it is not visible and hide the sidebar when it is visible.
     */
    toggleSidebar(): void {
        this.showSidebar = !this.showSidebar;
    }

    openOptions(): void {
        this.toggleSidebar();
        this.showOptions = true;
    }

    optionsClosed(isClosed: boolean): void {
        //TODO isClosed inputparamenter vll entfernen
        this.showOptions = false;
    }

    closeOverlay(): void {
        if (this.showOptions) {
            this.optionsClosed(true);
        } else if (this.showSidebar) {
            this.toggleSidebar();
        }
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
                this.updateRecentNotificationCount();
            }, 2000);
        });
    }

    /**
     * Check scroll position and load more notifications if the user scrolled to the end.
     */
    onScroll(): void {
        const container = document.getElementById('notification-sidebar-container');
        const threshold = 350;
        if (container) {
            const height = container.scrollHeight - container.offsetHeight;
            if (height > threshold && container.scrollTop > height - threshold) {
                this.loadNotifications();
            }
        }
    }

    private loadNotifications(): void {
        if (!this.loading && (this.totalNotifications === 0 || this.notifications.length < this.totalNotifications)) {
            this.loading = true;
            this.notificationService
                .query({
                    page: this.page,
                    size: this.notificationsPerPage,
                    sort: ['notificationDate,desc'],
                })
                .subscribe(
                    (res: HttpResponse<Notification[]>) => this.loadNotificationsSuccess(res.body!, res.headers),
                    (res: HttpErrorResponse) => (this.error = res.message),
                );
        }
    }

    private loadNotificationsSuccess(notifications: Notification[], headers: HttpHeaders): void {
        this.totalNotifications = Number(headers.get('X-Total-Count')!);
        this.addNotifications(notifications);
        this.page += 1;
        this.loading = false;
    }

    private subscribeToNotificationUpdates(): void {
        this.notificationService.subscribeToNotificationUpdates().subscribe((notification: Notification) => {
            // Increase total notifications count if the notification does not already exist.
            if (!this.notifications.some(({ id }) => id === notification.id)) {
                this.totalNotifications += 1;
            }
            this.addNotifications([notification]);
        });
    }

    private addNotifications(notifications: Notification[]): void {
        if (notifications) {
            notifications.forEach((notification: Notification) => {
                if (!this.notifications.some(({ id }) => id === notification.id) && notification.notificationDate) {
                    this.notifications.push(notification);
                }
            });
            this.updateNotifications();
        }
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
