import { Component, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { User } from 'app/core/user/user.model';
import { UserService } from 'app/core/user/user.service';
import * as moment from 'moment';
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
    notifications: Notification[] = [];
    sortedNotifications: Notification[] = [];
    currentUser: User;
    showSidebar = false;
    page = 0;
    notificationsPerPage = 25;
    notificationCount = 0;
    totalNotifications = 0;
    error: string | null = null;

    constructor(private notificationService: NotificationService, private userService: UserService, private accountService: AccountService) {}

    ngOnInit(): void {
        // TODO: can we remove the first three lines here? Currently the service method is called twice.
        if (this.accountService.isAuthenticated()) {
            this.loadNotifications();
        }
        this.accountService.getAuthenticationState().subscribe((user: User | null) => {
            if (user) {
                this.loadNotifications();
            }
        });
    }

    private loadNotifications(): void {
        // Query active system notification, recent and non-recent notifications.
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

    startNotification(notification: Notification): void {
        this.notificationService.interpretNotification(notification as GroupNotification);
    }

    updateNotifications(): void {
        this.sortedNotifications = this.notifications.sort((a: Notification, b: Notification) => {
            return moment(b.notificationDate!).valueOf() - moment(a.notificationDate!).valueOf();
        });
        this.updateNotificationCount();
    }

    updateNotificationCount(): void {
        if (!this.notifications) {
            this.notificationCount = 0;
        } else {
            this.notificationCount = this.notifications.length;
        }
    }

    updateNotificationDate(): void {
        this.userService.updateUserNotificationDate().subscribe((res: HttpResponse<User>) => {
            res.body!.lastNotificationRead = moment();
            setTimeout(() => {
                this.currentUser = res.body!;
            }, 1500);
        });
    }

    toggleSidebar(): void {
        this.showSidebar = !this.showSidebar;
    }
}
