import { Component, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { User } from 'app/core/user/user.model';
import { UserService } from 'app/core/user/user.service';
import dayjs from 'dayjs';
import { GroupNotification } from 'app/entities/group-notification.model';
import { LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE, Notification } from 'app/entities/notification.model';
import { AccountService } from 'app/core/auth/account.service';
import { NotificationService } from 'app/shared/notification/notification.service';
import { UserSettingsService } from 'app/shared/user-settings/user-settings.service';
import { NotificationSetting } from 'app/shared/user-settings/notification-settings/notification-settings-structure';
import { Subscription } from 'rxjs';
import { NotificationSettingsService } from 'app/shared/user-settings/notification-settings/notification-settings.service';
import { UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { Setting } from 'app/shared/user-settings/user-settings.model';

export const reloadNotificationSideBarMessage = 'reloadNotificationsInNotificationSideBar';

@Component({
    selector: 'jhi-notification-sidebar',
    templateUrl: './notification-sidebar.component.html',
    styleUrls: ['./notification-sidebar.scss'],
})
export class NotificationSidebarComponent implements OnInit {
    // HTML template related
    showSidebar = false;
    showAllNotificationsInSideBar = true;
    loading = false;

    // notification logic related
    notifications: Notification[] = [];
    sortedNotifications: Notification[] = [];
    recentNotificationCount = 0;
    totalNotifications = 0;
    lastNotificationRead?: dayjs.Dayjs;
    hideNotificationsUntil: dayjs.Dayjs | null;
    page = 0;
    notificationsPerPage = 25;
    error?: string;

    // notification settings related
    notificationSettings: NotificationSetting[] = [];
    notificationTitleActivationMap: Map<string, boolean> = new Map<string, boolean>();
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
                this.loadNotificationSettings();
                this.listenForNotificationSettingsChanges();
                this.applyNotificationVisibilityAndLoadNotifications();
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
     * Starts the process to show or hide all notifications in the sidebar
     */
    toggleNotificationDisplay(): void {
        if (this.showAllNotificationsInSideBar) {
            // all notifications (even the hidden ones) should be displayed
            this.hideNotificationsUntil = null;
        } else {
            // hide all currently displayed notifications
            this.hideNotificationsUntil = dayjs();
        }
        this.userService.updateNotificationVisibility(this.hideNotificationsUntil).subscribe((res: HttpResponse<dayjs.Dayjs | null>) => {
            this.hideNotificationsUntil = res.body;
            this.resetNotificationsInSidebar();
            this.showAllNotificationsInSideBar = !this.showAllNotificationsInSideBar;
        });
    }

    /**
     * Update the user's lastNotificationRead setting. As this method will be executed when the user opens the sidebar, the
     * component's lastNotificationRead attribute will be updated only after two seconds so that the notification `new` badges
     * won't disappear immediately.
     */
    updateLastNotificationRead(): void {
        this.userService.updateLastNotificationRead().subscribe(() => {
            const lastNotificationReadNow = dayjs();
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
                .queryNotificationsFilteredBySettings({
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
        this.addNotifications(this.filterLoadedNotifications(notifications));
        this.page += 1;
        this.loading = false;
    }

    // filter out every exam related notification
    private filterLoadedNotifications(notifications: Notification[]): Notification[] {
        /* TODO check if necessary (because filter should already work on server side)
        if (this.hideNotificationsUntil !== null) {
            // if the user wants to hide certain notifications only display those that arrived after the user pressed that (hide)button
            notifications = notifications.filter((notification) => notification.notificationDate?.isAfter(this.hideNotificationsUntil));
        }
         */
        return notifications.filter((notification) => notification.title !== LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE);
    }

    private subscribeToNotificationUpdates(): void {
        this.notificationService.subscribeToNotificationUpdates().subscribe((notification: Notification) => {
            if (this.notificationSettingsService.isNotificationAllowedBySettings(notification, this.notificationTitleActivationMap)) {
                // ignores live exam notifications because the sidebar is not visible during the exam mode
                if (notification.title === LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE) {
                    return;
                }

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
            return dayjs(b.notificationDate!).valueOf() - dayjs(a.notificationDate!).valueOf();
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
    private resetNotificationSidebarsWithSettings(): void {
        // reset notification settings
        this.notificationSettings = [];
        this.notificationTitleActivationMap = new Map<string, boolean>();
        this.loadNotificationSettings();
        this.resetNotificationsInSidebar();
    }

    /**
     * Loads the notifications and updates their visibility based on their creation/notification date
     */
    private applyNotificationVisibilityAndLoadNotifications(): void {
        this.userService.getNotificationVisibility().subscribe(
            (res: HttpResponse<dayjs.Dayjs | null>) => {
                this.hideNotificationsUntil = res.body;
                this.loadNotifications();
            },
            () => this.loadNotifications(),
        );
    }

    /**
     * Clears all currently loaded notifications, afterwards fetches updated once
     * E.g. is used to update the view after the user toggles the button to show/hide all notifications
     */
    private resetNotificationsInSidebar(): void {
        this.notifications = [];
        this.sortedNotifications = [];
        this.recentNotificationCount = 0;
        this.totalNotifications = 0;
        this.page = 0;
        this.applyNotificationVisibilityAndLoadNotifications();
    }

    // notification settings related methods

    /**
     * Loads the notifications settings
     */
    private loadNotificationSettings(): void {
        this.userSettingsService.loadSettings(UserSettingsCategory.NOTIFICATION_SETTINGS).subscribe(
            (res: HttpResponse<Setting[]>) => {
                this.notificationSettings = this.userSettingsService.loadSettingsSuccessAsIndividualSettings(
                    res.body!,
                    UserSettingsCategory.NOTIFICATION_SETTINGS,
                ) as NotificationSetting[];
                this.notificationTitleActivationMap = this.notificationSettingsService.createUpdatedNotificationTitleActivationMap(this.notificationSettings);
            },
            (res: HttpErrorResponse) => (this.error = res.message),
        );
    }

    /**
     * Subscribes and listens for changes related to notifications
     * When a fitting event arrives resets the notification side bar to update the view
     */
    private listenForNotificationSettingsChanges(): void {
        this.subscriptionToNotificationSettingsChanges = this.userSettingsService.userSettingsChangeEvent.subscribe((changeMessage) => {
            if (changeMessage === reloadNotificationSideBarMessage) {
                this.resetNotificationSidebarsWithSettings();
            }
        });
    }
}
