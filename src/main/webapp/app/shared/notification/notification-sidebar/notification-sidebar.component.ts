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
import { OptionCore, UserSettingsService } from 'app/shared/user-settings/user-settings.service';
import { defaultNotificationSettings } from 'app/shared/user-settings/notification-settings/notification-settings.default';

@Component({
    selector: 'jhi-notification-sidebar',
    templateUrl: './notification-sidebar.component.html',
    styleUrls: ['./notification-sidebar.scss'],
})
export class NotificationSidebarComponent implements OnInit {
    showSidebar = false;
    loading = false;
    notifications: Notification[] = [];
    sortedNotifications: Notification[] = [];
    recentNotificationCount = 0;
    totalNotifications = 0;
    lastNotificationRead?: Moment;
    page = 0;
    notificationsPerPage = 25;
    error?: string;

    notificationOptions: OptionCore[] = [];
    userSettingsCategory = defaultNotificationSettings.category;

    //deactivatedNotificationTypes : Map<NotificationType, boolean> = new Map<NotificationType, boolean>();

    constructor(
        private notificationService: NotificationService,
        private userService: UserService,
        private accountService: AccountService,
        private userSettingsService: UserSettingsService,
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
                this.loadNotificationsWithAppliedSettings();
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
        //filter loaded notifications based on current notification settings
        notifications = this.filterNotificationsBasedOnNotificationSettings(notifications);

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

    private loadNotificationSettings(): void {
        this.userSettingsService.loadUserOptions(this.userSettingsCategory).subscribe((res: HttpResponse<OptionCore[]>) => {
            this.notificationOptions = this.userSettingsService.loadUserOptionCoresSuccessAsOptionCores(res.body!, res.headers, this.userSettingsCategory);
            //(res: HttpErrorResponse) => (this.error = res.message) TODO
        });
    }

    private loadNotificationsWithAppliedSettings(): void {
        //load notification settings
        this.userSettingsService.loadUserOptions(this.userSettingsCategory).subscribe((res: HttpResponse<OptionCore[]>) => {
            this.notificationOptions = this.userSettingsService.loadUserOptionCoresSuccessAsOptionCores(res.body!, res.headers, this.userSettingsCategory);
            //(res: HttpErrorResponse) => (this.error = res.message) TODO

            this.loadNotifications();
        });
    }

    /*
    this.fillDeactivatedNotificationTypesMapBasedOnOptions() : void {
        let correspondingNotificationTypes : NotificationType[] = [];
        for(int i = 0; i < this.notificationOptions.length; i++) {
            //e.g. this.notificationOptions[i].optionSpecifier = 'notification.instructor-exclusive-notification.course-and-exam-archiving-started';
            // -> corresponding NotificationTypes = [COURSE_ARCHIVE_STARTED, EXAM_ARCHIVE_STARTED]
            // -> find these types in deactivatedNotificationTypes (Map)
            // if not present yet in map -> add entry to map if this.notificationOptions[i].webapp == false
            // else find entry in map and check if it is still false, if no longer, set to true (should be easier then removing the entry) -> filter only those with false set

           correspondingNotificationTypes = findCorrespondingNotificationTypesForGivenNotificationOption(this.notificationOptions[i].optionSpecifier);
           ... (look at written algorithm above)
        }
    }
     */

    private filterNotificationsBasedOnNotificationSettings(unfilteredNotifications: Notification[]): Notification[] {
        debugger;
        return unfilteredNotifications.filter(this.notificationSettingsFilter);
    }

    private notificationSettingsFilter(notification: Notification): boolean {
        //check what notificatioType this notification has and if it is set to false in map
        // if it is set to false -> return false
        // if it is set to true or not present in map (i.e. unchangable notification-"option" not shown to the user)

        return true; //todo remove dummy
    }
    /*
    private findCorrespondingNotificationTypesForGivenNotificationOption(notificationOption : OptionCore) : NotificationType[] {
        switch (notificationOption.optionSpecifier) {
            case 'notification.exercise-notification.exercise-created-or-started': {
                return [NotificationType.EXERCISE_CREATED];
            }
            ...
            case 'notification.instructor-exclusive-notification.course-and-exam-archiving-started' : {
                return [NotificationType.EXAM_ARCHIVE_STARTED, NotificationType.COURSE_ARCHIVE_STARTED];
            }
        }
    }
     */
}
