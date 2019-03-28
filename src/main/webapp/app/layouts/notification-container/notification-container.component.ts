import { Component } from '@angular/core';
import { Notification, NotificationService } from 'app/entities/notification';
import { HttpResponse } from '@angular/common/http';
import { AccountService, User, UserService } from 'app/core';
import * as moment from 'moment';

@Component({
    selector: 'jhi-notification-container',
    templateUrl: './notification-container.component.html',
    styleUrls: ['./notification-container.scss']
})

export class NotificationContainerComponent {
    notifications: Notification[] = [];
    currentUser: User;
    notificationCount: number = 0;

    constructor(private notificationService: NotificationService, private userService: UserService, private accountService: AccountService) {

    }

    ngOnInit() {
        this.notificationService.getRecentNotificationsForUser().subscribe((res: HttpResponse<Notification[]>) => {
            this.notifications = res.body;
            this.updateNotificationCount();
        });
        this.accountService.getAuthenticationState().subscribe((res: User) => {
            this.currentUser = res;
            this.updateNotificationCount();
        });
    }

    startNotification(notification: Notification) {
        this.notificationService.interpretNotification(notification)
    }

    updateNotificationCount() {
        if (!this.notifications) {
            return this.notificationCount = 0;
        }
        if (!this.currentUser) {
            return this.notificationCount = this.notifications.length;
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
