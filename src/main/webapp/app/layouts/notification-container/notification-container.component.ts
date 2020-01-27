import { Component, OnInit } from '@angular/core';
import { Notification, NotificationService } from 'app/entities/notification';
import { HttpResponse } from '@angular/common/http';
import { User } from 'app/core/user/user.model';
import { UserService } from 'app/core/user/user.service';
import * as moment from 'moment';
import { GroupNotification } from 'app/entities/group-notification';
import { AccountService } from 'app/core/auth/account.service';

@Component({
    selector: 'jhi-notification-container',
    templateUrl: './notification-container.component.html',
    styleUrls: ['./notification-container.scss'],
})
export class NotificationContainerComponent implements OnInit {
    notifications: Notification[] = [];
    sortedNotifications: Notification[] = [];
    currentUser: User;
    notificationCount = 0;

    constructor(private notificationService: NotificationService, private userService: UserService, private accountService: AccountService) {}

    ngOnInit() {
        if (this.accountService.isAuthenticated()) {
            this.loadNotifications();
        }
        this.accountService.getAuthenticationState().subscribe((user: User | null) => {
            if (user) {
                this.loadNotifications();
            }
        });
    }

    private loadNotifications() {
        this.notificationService.getRecentNotificationsForUser().subscribe((notifications: Notification[]) => {
            this.notifications = notifications;
            this.updateNotifications();
        });
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

    startNotification(notification: Notification) {
        this.notificationService.interpretNotification(notification as GroupNotification);
    }

    updateNotifications() {
        this.sortedNotifications = this.notifications.sort((a: Notification, b: Notification) => {
            return moment(b.notificationDate!).valueOf() - moment(a.notificationDate!).valueOf();
        });
        this.updateNotificationCount();
    }

    updateNotificationCount() {
        if (!this.notifications) {
            return (this.notificationCount = 0);
        }
        if (!this.currentUser) {
            return (this.notificationCount = this.notifications.length);
        }
        this.notificationCount = this.notifications.filter((el: Notification) => el.notificationDate!.isAfter(this.currentUser.lastNotificationRead!)).length;
    }

    updateNotificationDate() {
        this.userService.updateUserNotificationDate().subscribe((res: HttpResponse<User>) => {
            res.body!.lastNotificationRead = moment();
            setTimeout(() => {
                this.currentUser = res.body!;
                this.updateNotifications();
            }, 1500);
        });
    }
}
