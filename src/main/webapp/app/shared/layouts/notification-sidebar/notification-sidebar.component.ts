import { Component, OnInit } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
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
    notificationCount = 0;
    showSidebar = false;

    constructor(private notificationService: NotificationService, private userService: UserService, private accountService: AccountService) {}

    /** Lifecycle hook which is called on initialisation. It subscribes to the user authentication state and triggers {@link loadNotifications} if the user is authenticated.
     * See {@link accountService~isAuthenticated}
     */
    ngOnInit(): void {
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

    /** Wrapper function for {@link notificationService~interpretNotification}. Starts the notification.
     * @method
     * @param notification {Notification}
     */
    startNotification(notification: Notification): void {
        this.notificationService.interpretNotification(notification as GroupNotification);
    }

    /** Updates the notifications by sorting them and and triggers an notification count update.
     * See {@link updateNotificationCount}
     * @method
     */
    updateNotifications(): void {
        this.sortedNotifications = this.notifications.sort((a: Notification, b: Notification) => {
            return moment(b.notificationDate!).valueOf() - moment(a.notificationDate!).valueOf();
        });
        this.updateNotificationCount();
    }

    /** Updates the current notifications count and sets it in {@link notificationCount}
     * @method
     */
    updateNotificationCount(): void {
        if (!this.notifications) {
            this.notificationCount = 0;
        } else if (!this.currentUser) {
            this.notificationCount = this.notifications.length;
        } else {
            this.notificationCount = this.notifications.filter((el: Notification) => el.notificationDate!.isAfter(this.currentUser.lastNotificationRead!)).length;
        }
    }

    /** Updates the notification date for users every 1,5 seconds.
     * See {@link updateNotifications}
     * @method
     *
     */
    updateNotificationDate(): void {
        this.userService.updateUserNotificationDate().subscribe((res: HttpResponse<User>) => {
            res.body!.lastNotificationRead = moment();
            setTimeout(() => {
                this.currentUser = res.body!;
                this.updateNotifications();
            }, 1500);
        });
    }

    /** Toggles the sidebar from opened to collapsed and vice versa using the {@link showSidebar} flag.
     * @method
     */
    toggleSidebar(): void {
        this.showSidebar = !this.showSidebar;
    }
}
