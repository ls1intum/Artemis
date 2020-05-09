import { Component, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { User } from 'app/core/user/user.model';
import { UserService } from 'app/core/user/user.service';
import * as moment from 'moment';
import { Moment } from 'moment';
import { GroupNotification } from 'app/entities/group-notification.model';
import { Notification } from 'app/entities/notification.model';
import { AccountService } from 'app/core/auth/account.service';
import { NotificationService } from 'app/overview/notification/notification.service';

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

    ngOnInit(): void {
        // TODO: can we remove the first three lines here? Currently the service method is called twice.
        if (this.accountService.isAuthenticated()) {
            this.loadNotifications();
        }
        this.accountService.getAuthenticationState().subscribe((user: User | null) => {
            if (user) {
                if (user.lastNotificationRead) {
                    this.lastNotificationRead = user.lastNotificationRead;
                }
                this.loadNotifications();
            }
        });
    }

    private loadNotifications(): void {
        // Query recent and non-recent notifications.
        this.notificationService
            .queryNew({
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

    startNotification(notification: Notification): void {
        this.notificationService.interpretNotification(notification as GroupNotification);
    }

    updateNotificationDate(): void {
        this.userService.updateUserNotificationDate().subscribe((res: HttpResponse<User>) => {
            res.body!.lastNotificationRead = moment();
            setTimeout(() => {
                this.lastNotificationRead = res.body!.lastNotificationRead;
            }, 2000);
        });
    }

    toggleSidebar(): void {
        this.showSidebar = !this.showSidebar;
    }
}
