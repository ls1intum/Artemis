import { Component } from '@angular/core';
import { Notification, NotificationService, NotificationType } from 'app/entities/notification';
import { HttpResponse } from '@angular/common/http';
import { AccountService, User, UserService } from 'app/core';
import * as moment from 'moment';
import { GroupNotification } from 'app/entities/group-notification';

@Component({
    selector: 'jhi-notification-container',
    templateUrl: './notification-container.component.html',
    styleUrls: ['./notification-container.scss'],
})
export class NotificationContainerComponent {
    notifications: Notification[] = [];
    currentUser: User;
    notificationCount: number = 0;

    constructor(private notificationService: NotificationService, private userService: UserService, private accountService: AccountService) {}

    ngOnInit() {
        this.notificationService.getRecentNotificationsForUser().subscribe((res: HttpResponse<Notification[]>) => {
            this.notifications = res.body;
            this.updateNotificationCount();
        });
        this.accountService.getAuthenticationState().subscribe((res: User) => {
            this.currentUser = res;
            this.updateNotificationCount();
        });
        this.notificationService.subscribeToSocketMessages().subscribe((notification: Notification) => {
            if (notification) {
                notification.notificationDate = notification.notificationDate ? moment(notification.notificationDate) : null;
                this.notifications.push(notification);
                this.updateNotificationCount();
            }
        });
    }

    startNotification(notification: Notification) {
        if (notification.notificationType === NotificationType.GROUP) {
            this.notificationService.interpretNotification(notification as GroupNotification);
        }
    }

    updateNotificationCount() {
        if (!this.notifications) {
            return (this.notificationCount = 0);
        }
        if (!this.currentUser) {
            return (this.notificationCount = this.notifications.length);
        }
        this.notificationCount = this.notifications.filter((el: Notification) => el.notificationDate.isAfter(this.currentUser.lastNotificationRead)).length;
    }

    updateNotificationDate() {
        this.userService.updateUserNotificationDate().subscribe((res: HttpResponse<User>) => {
            res.body.lastNotificationRead = moment();
            setTimeout(() => {
                this.currentUser = res.body;
                this.updateNotificationCount();
            }, 500);
        });
    }
}
