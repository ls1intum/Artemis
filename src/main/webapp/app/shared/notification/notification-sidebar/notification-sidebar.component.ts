import { Component, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { User } from 'app/core/user/user.model';
import { UserService } from 'app/core/user/user.service';
import * as moment from 'moment';
import { Moment } from 'moment';
import { GroupNotification } from 'app/entities/group-notification.model';
import { Notification, OriginalNotificationType } from 'app/entities/notification.model';
import { AccountService } from 'app/core/auth/account.service';
import { NotificationService } from 'app/shared/notification/notification.service';
import { UserSettingsService } from 'app/shared/user-settings/user-settings.service';
import { NotificationOptionCore } from 'app/shared/user-settings/notification-settings/notification-settings.default';
import { Subscription } from 'rxjs';
import { NotificationSettingsService } from 'app/shared/user-settings/notification-settings/notification-settings.service';
import { UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { OptionCore } from 'app/shared/user-settings/user-settings.model';

export const reloadNotificationSideBarMessage = 'reloadNotificationsInNotificationSideBar';

@Component({
    selector: 'jhi-notification-sidebar',
    templateUrl: './notification-sidebar.component.html',
    styleUrls: ['./notification-sidebar.scss'],
})
export class NotificationSidebarComponent implements OnInit {
    // HTML template related
    showSidebar = false;
    loading = false;

    // notification logic related
    notifications: Notification[] = [];
    sortedNotifications: Notification[] = [];
    recentNotificationCount = 0;
    totalNotifications = 0;
    lastNotificationRead?: Moment;
    page = 0;
    notificationsPerPage = 25;
    error?: string;

    // notification settings related
    notificationOptionCores: NotificationOptionCore[] = [];
    originalNotificationTypesActivationMap: Map<OriginalNotificationType, boolean> = new Map<OriginalNotificationType, boolean>();
    subscriptionToNotificationSettingsChanges: Subscription;

    constructor(
        private notificationService: NotificationService,
        private userService: UserService,
        private accountService: AccountService,
        private userSettingsService: UserSettingsService,
        private notificationSettingsService: NotificationSettingsService,
    ) {}

    /**
     * Load notifications when user is authenticated on component initialization.
     */
    ngOnInit(): void {
        this.accountService.getAuthenticationState().subscribe((user: User | undefined) => {
            if (user) {
                if (user.lastNotificationRead) {
                    this.lastNotificationRead = user.lastNotificationRead;
                }
                this.loadNotificationSetting();
                this.listenForNotificationSettingsChanges();
                this.loadNotifications();
                this.subscribeToNotificationUpdates();
            }
        });
    }

    // HTML template related methods

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

    /**
     * Show the sidebar when it is not visible and hide the sidebar when it is visible.
     */
    toggleSidebar(): void {
        this.showSidebar = !this.showSidebar;
    }

    // notification logic related methods

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

    private loadNotifications(): void {
        if (!this.loading && (this.totalNotifications === 0 || this.notifications.length < this.totalNotifications)) {
            this.loading = true;
            this.notificationService
                .queryFiltered({
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
            if (this.notificationSettingsService.isNotificationAllowedBySettings(notification, this.originalNotificationTypesActivationMap)) {
                // Increase total notifications count if the notification does not already exist.
                if (!this.notifications.some(({ id }) => id === notification.id)) {
                    this.totalNotifications += 1;
                }
                this.addNotifications([notification]);
            }
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

    /**
     * Clears all currently loaded notifications and settings, afterwards fetches updated once
     * E.g. is used to update the view after the user changed the notification settings
     */
    private resetNotificationSidebars(): void {
        // reset notification settings
        this.notificationOptionCores = [];
        this.originalNotificationTypesActivationMap = new Map<OriginalNotificationType, boolean>();
        this.loadNotificationSetting();

        //reset notifications
        this.notifications = [];
        this.sortedNotifications = [];
        this.recentNotificationCount = 0;
        this.totalNotifications = 0;
        this.page = 0;
        this.loadNotifications();
    }

    // notification settings related methods

    /**
     * Loads the notifications settings, i.e. the respective option cores
     */
    private loadNotificationSetting(): void {
        this.userSettingsService.loadUserOptions(UserSettingsCategory.NOTIFICATION_SETTINGS).subscribe((res: HttpResponse<OptionCore[]>) => {
            this.notificationOptionCores = this.userSettingsService.loadUserOptionCoresSuccessAsOptionCores(
                res.body!,
                res.headers,
                UserSettingsCategory.NOTIFICATION_SETTINGS,
            ) as NotificationOptionCore[];
            this.originalNotificationTypesActivationMap = this.notificationSettingsService.updateOriginalNotificationTypeActivationMap(this.notificationOptionCores);
            //(res: HttpErrorResponse) => (this.error = res.message) TODO
        });
    }

    /**
     * Subscribes and listens for changes related to notifications
     * If a fitting event arrives resets the notification side bar to update the view
     */
    private listenForNotificationSettingsChanges(): void {
        this.subscriptionToNotificationSettingsChanges = this.userSettingsService.userSettingsChangeEvent.subscribe((changeMessage) => {
            if (changeMessage === reloadNotificationSideBarMessage) {
                this.resetNotificationSidebars();
            }
        });
    }
}
