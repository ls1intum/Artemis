import { ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { User } from 'app/core/user/user.model';
import { UserService } from 'app/core/user/user.service';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import dayjs from 'dayjs/esm';
import { GroupNotification } from 'app/entities/group-notification.model';
import { LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE, NEW_MESSAGE_TITLE, Notification } from 'app/entities/notification.model';
import { AccountService } from 'app/core/auth/account.service';
import { NotificationService } from 'app/shared/notification/notification.service';
import { Subscription } from 'rxjs';
import { faArchive, faBell, faCircleNotch, faCog, faEye, faTimes } from '@fortawesome/free-solid-svg-icons';
import { SessionStorageService } from 'ngx-webstorage';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { translationNotFoundMessage } from 'app/core/config/translation.config';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

export const LAST_READ_STORAGE_KEY = 'lastNotificationRead';
const IRRELEVANT_NOTIFICATION_TITLES = [NEW_MESSAGE_TITLE, LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE];

@Component({
    selector: 'jhi-notification-sidebar',
    templateUrl: './notification-sidebar.component.html',
    styleUrls: ['./notification-sidebar.scss'],
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule],
    standalone: true,
})
export class NotificationSidebarComponent implements OnInit, OnDestroy {
    private notificationService = inject(NotificationService);
    private userService = inject(UserService);
    private accountService = inject(AccountService);
    private sessionStorageService = inject(SessionStorageService);
    private changeDetector = inject(ChangeDetectorRef);
    private artemisTranslatePipe = inject(ArtemisTranslatePipe);

    // HTML template related
    showSidebar = false;
    showButtonToHideCurrentlyDisplayedNotifications = true;

    // notification logic related
    sortedNotifications: Notification[] = [];
    recentNotificationCount = 0;
    lastNotificationRead?: dayjs.Dayjs;
    maxNotificationLength = 300;
    error?: string;
    loading = false;
    totalNotifications = 0;

    readonly documentationType: DocumentationType = 'Notifications';

    subscriptions: Subscription[] = [];

    // Icons
    faTimes = faTimes;
    faCircleNotch = faCircleNotch;
    faBell = faBell;
    faCog = faCog;
    faArchive = faArchive;
    faEye = faEye;

    ngOnDestroy(): void {
        this.subscriptions.forEach((subscription) => subscription.unsubscribe());
    }

    /**
     * Load notifications when user is authenticated on component initialization.
     */
    ngOnInit(): void {
        this.accountService.getAuthenticationState().subscribe((user: User | undefined) => {
            if (user) {
                // Check if we have a newer notification read date in session storage to prevent marking old notifications as new on a rerender
                // If we have it, use the latest of both dates

                const sessionLastNotificationReadRaw = this.sessionStorageService.retrieve(LAST_READ_STORAGE_KEY);
                const sessionLastNotificationReadDayJs = sessionLastNotificationReadRaw ? dayjs(sessionLastNotificationReadRaw) : undefined;

                if (user.lastNotificationRead && !sessionLastNotificationReadDayJs) {
                    this.lastNotificationRead = user.lastNotificationRead;
                } else if (!user.lastNotificationRead && sessionLastNotificationReadDayJs) {
                    this.lastNotificationRead = sessionLastNotificationReadDayJs;
                } else if (user.lastNotificationRead && sessionLastNotificationReadDayJs) {
                    if (sessionLastNotificationReadDayJs.isBefore(user.lastNotificationRead)) {
                        this.lastNotificationRead = user.lastNotificationRead;
                    } else {
                        this.lastNotificationRead = sessionLastNotificationReadDayJs;
                    }
                }

                this.subscribeToNotificationUpdates();
            } else {
                this.sessionStorageService.clear(LAST_READ_STORAGE_KEY);
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
                this.notificationService.incrementPageAndLoad();
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
        this.showButtonToHideCurrentlyDisplayedNotifications = !this.showButtonToHideCurrentlyDisplayedNotifications;
        this.userService.updateNotificationVisibility(this.showButtonToHideCurrentlyDisplayedNotifications).subscribe(() => {
            this.notificationService.resetAndLoad();
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
                this.sessionStorageService.store(LAST_READ_STORAGE_KEY, lastNotificationReadNow);
                this.updateRecentNotificationCount();
            }, 2000);
        });
    }

    /**
     * Returns the translated text for the placeholder of the notification text of the provided notification.
     * If the notification is a legacy notification and therefor the text is not a placeholder
     * it just returns the provided text for the notification text
     * @param notification {Notification}
     */
    getNotificationTitleTranslation(notification: Notification): string {
        const translation = this.artemisTranslatePipe.transform(notification.title);
        if (translation?.includes(translationNotFoundMessage)) {
            return notification.title ?? 'No title found';
        }
        return translation;
    }

    /**
     * Returns the translated text for the placeholder of the notification text of the provided notification.
     * If the notification is a legacy notification and therefor the text is not a placeholder
     * it just returns the provided text for the notification text
     * @param notification {Notification}
     */
    getNotificationTextTranslation(notification: Notification): string {
        return this.notificationService.getNotificationTextTranslation(notification, this.maxNotificationLength);
    }

    private subscribeToNotificationUpdates(): void {
        this.subscriptions.push(
            this.notificationService.subscribeToNotificationUpdates().subscribe((notifications: Notification[]) => {
                const filteredNotifications = this.filterLoadedNotifications(notifications);
                this.updateSortedNotifications(filteredNotifications);
            }),
        );
        this.subscriptions.push(this.notificationService.subscribeToTotalNotificationCountUpdates().subscribe((count: number) => (this.totalNotifications = count)));
        this.subscriptions.push(this.notificationService.subscribeToLoadingStateUpdates().subscribe((loading: boolean) => (this.loading = loading)));
    }

    private filterLoadedNotifications(notifications: Notification[]): Notification[] {
        return notifications.filter((notification) => notification.title && !IRRELEVANT_NOTIFICATION_TITLES.includes(notification.title));
    }

    private updateSortedNotifications(notifications: Notification[]): void {
        this.sortedNotifications = notifications.sort((a: Notification, b: Notification) => {
            return dayjs(b.notificationDate!).valueOf() - dayjs(a.notificationDate!).valueOf();
        });
        this.updateRecentNotificationCount();
    }

    private updateRecentNotificationCount(): void {
        if (!this.sortedNotifications) {
            this.recentNotificationCount = 0;
        } else if (this.lastNotificationRead) {
            this.recentNotificationCount = this.sortedNotifications.filter((notification) => {
                return notification.notificationDate && notification.notificationDate.isAfter(this.lastNotificationRead!);
            }).length;
        } else {
            this.recentNotificationCount = this.sortedNotifications.length;
        }

        if (!this.sortedNotifications || this.sortedNotifications.length === 0) {
            // if no notifications are currently loaded show the button to display all saved/archived ones
            this.showButtonToHideCurrentlyDisplayedNotifications = false;
        } else {
            // some notifications are currently loaded, thus show the button to hide currently displayed ones
            this.showButtonToHideCurrentlyDisplayedNotifications = true;
        }
        this.changeDetector.detectChanges();
    }
}
