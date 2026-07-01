import { AfterViewInit, Component, ElementRef, HostListener, OnDestroy, OnInit, effect, inject, input, signal, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { faBell, faCog, faEnvelopeOpen, faFilter, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { CourseNotificationBubbleComponent } from 'app/notification/course-notification/course-notification-bubble/course-notification-bubble.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { CourseNotificationCategory } from 'app/notification/shared/entities/course-notification/course-notification-category';
import { CourseNotification } from 'app/notification/shared/entities/course-notification/course-notification';
import { CourseNotificationComponent } from 'app/notification/course-notification/course-notification/course-notification.component';
import { CourseNotificationService } from 'app/notification/course-notification/course-notification.service';
import { Subscription, fromEvent } from 'rxjs';
import { CourseNotificationViewingStatus } from 'app/notification/shared/entities/course-notification/course-notification-viewing-status';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { CourseNotificationSettingPreset } from 'app/notification/shared/entities/course-notification/course-notification-setting-preset';
import { CourseNotificationInfo } from 'app/notification/shared/entities/course-notification/course-notification-info';
import { CourseNotificationSettingInfo } from 'app/notification/shared/entities/course-notification/course-notification-setting-info';
import { CourseNotificationSettingService } from 'app/notification/course-notification/course-notification-setting.service';
import { CourseNotificationPresetPickerComponent } from 'app/notification/course-notification/course-notification-preset-picker/course-notification-preset-picker.component';

/**
 * Component that displays a comprehensive overview of course notifications.
 * Features a dropdown interface with category filtering, infinite scrolling,
 * and notification status management.
 */
@Component({
    selector: 'jhi-course-notification-overview',
    imports: [
        FontAwesomeModule,
        CourseNotificationBubbleComponent,
        CommonModule,
        TranslateDirective,
        CourseNotificationComponent,
        ArtemisTranslatePipe,
        RouterLink,
        ButtonModule,
        TooltipModule,
        CourseNotificationPresetPickerComponent,
    ],
    templateUrl: './course-notification-overview.component.html',
    styleUrls: ['./course-notification-overview.component.scss'],
})
export class CourseNotificationOverviewComponent implements OnDestroy, OnInit, AfterViewInit {
    readonly courseId = input.required<number>();

    private elementRef = inject(ElementRef);
    private courseNotificationService = inject(CourseNotificationService);
    private courseNotificationSettingService = inject(CourseNotificationSettingService);

    // Icons
    protected readonly faBell = faBell;
    protected readonly faCog = faCog;
    protected readonly faFilter = faFilter;
    protected readonly faEnvelopeOpen = faEnvelopeOpen;
    protected readonly faSpinner = faSpinner;

    protected readonly courseCategories = signal<string[]>([]);

    protected readonly selectableSettingPresets = signal<CourseNotificationSettingPreset[] | undefined>(undefined);
    protected readonly selectedSettingPreset = signal<CourseNotificationSettingPreset | undefined>(undefined);
    private info?: CourseNotificationInfo;
    private settingInfo?: CourseNotificationSettingInfo;

    protected readonly isShown = signal(false);
    protected selectedCategory = CourseNotificationCategory.GENERAL;
    protected notifications: CourseNotification[];
    protected readonly notificationsForSelectedCategory = signal<CourseNotification[]>([]);
    protected readonly courseNotificationCount = signal<number>(0);
    protected queryStartSize: number = 0;
    protected queryCount: number = 1;
    protected savedScrollPosition: number = 0;
    protected pagesFinished: boolean = false;
    protected readonly isLoading = signal<boolean>(false);
    private courseNotificationCountSubscription?: Subscription;
    private courseNotificationSubscription?: Subscription;
    private scrollContainer = viewChild<ElementRef>('scrollContainer');

    protected readonly CourseNotificationViewingStatus = CourseNotificationViewingStatus;

    constructor() {
        this.courseCategories.set(Object.keys(CourseNotificationCategory).filter((category) => isNaN(Number(category))));

        effect((onCleanup) => {
            const courseId = this.courseId();
            this.settingInfo = undefined;
            this.info = undefined;
            this.selectableSettingPresets.set(undefined);
            this.selectedSettingPreset.set(undefined);

            const tryInitializeCourseNotificationValues = () => {
                if (this.settingInfo && this.info) {
                    this.initializeCourseNotificationValues();
                }
            };

            const settingInfoSubscription = this.courseNotificationSettingService.getSettingInfo(courseId, false).subscribe((settingInfo) => {
                if (settingInfo) {
                    this.settingInfo = settingInfo;
                    tryInitializeCourseNotificationValues();
                }
            });

            const infoSubscription = this.courseNotificationService.getInfo().subscribe((info) => {
                if (info.body) {
                    this.info = info.body;
                    tryInitializeCourseNotificationValues();
                }
            });

            onCleanup(() => {
                settingInfoSubscription.unsubscribe();
                infoSubscription.unsubscribe();
            });
        });
    }

    ngAfterViewInit(): void {
        if (!this.scrollContainer()) {
            return;
        }

        fromEvent(this.scrollContainer()!.nativeElement, 'scroll')
            .pipe(debounceTime(200), distinctUntilChanged())
            .subscribe(() => {
                if (this.isScrolledToBottom()) {
                    this.savedScrollPosition = this.scrollContainer()!.nativeElement.scrollTop;
                    this.onScrollReachBottom();
                }
            });
    }

    ngOnInit(): void {
        this.courseNotificationCountSubscription = this.courseNotificationService.getNotificationCountForCourse$(this.courseId()).subscribe((count: number) => {
            this.courseNotificationCount.set(count);
        });
        this.courseNotificationSubscription = this.courseNotificationService.getNotificationsForCourse$(this.courseId()).subscribe((notifications) => {
            this.notifications = notifications;

            this.filterNotificationsIntoCurrentCategory();

            // Note: This is a temporary solution until server-side categorization paging is possible
            if (
                this.isLoading() &&
                !this.pagesFinished &&
                this.queryCount <= 3 &&
                this.notificationsForSelectedCategory().length < this.queryStartSize + this.courseNotificationService.pageSize
            ) {
                this.queryCount++;
                this.queryCurrentCategory();
            } else {
                this.isLoading.set(false);
                this.queryCount = 1;

                if (this.isShown()) {
                    setTimeout(() => {
                        this.scrollContainer()!.nativeElement.scrollTop = this.savedScrollPosition;
                    });
                    this.updateCurrentCategoryNotificationsToSeenOnServer();
                }
            }
        });
    }

    ngOnDestroy(): void {
        if (this.courseNotificationCountSubscription) {
            this.courseNotificationCountSubscription.unsubscribe();
        }
        if (this.courseNotificationSubscription) {
            this.courseNotificationSubscription.unsubscribe();
        }
    }

    /**
     * Toggles the visibility of the notification overlay.
     * When shown, may trigger loading of additional notifications.
     * When hidden, marks visible notifications as seen.
     */
    protected toggleOverlay() {
        this.isShown.update((shown) => !shown);

        if (!this.isShown()) {
            this.updateCurrentCategoryNotificationsToSeenOnClient();
        }

        if (this.notificationsForSelectedCategory().length < this.courseNotificationService.pageSize && !this.pagesFinished) {
            this.queryStartSize = this.notificationsForSelectedCategory().length;
            this.queryCurrentCategory();
        }
    }

    /**
     * Checks if a category is currently selected.
     *
     * @param categoryString - The category name to check
     * @returns Whether the specified category is currently selected
     */
    protected isCategorySelected(categoryString: string) {
        return CourseNotificationCategory[categoryString as keyof typeof CourseNotificationCategory] == this.selectedCategory;
    }

    /**
     * Handles selection of a notification category.
     * Updates notification list, marks current notifications as seen,
     * and may trigger loading of additional notifications.
     *
     * @param categoryString - The category name to select
     */
    protected selectCategory(categoryString: string) {
        this.updateCurrentCategoryNotificationsToSeenOnClient();

        this.selectedCategory = CourseNotificationCategory[categoryString as keyof typeof CourseNotificationCategory];

        this.filterNotificationsIntoCurrentCategory();

        this.updateCurrentCategoryNotificationsToSeenOnServer();

        if (!this.pagesFinished && this.notificationsForSelectedCategory().length < this.courseNotificationService.pageSize) {
            this.queryCurrentCategory();
        }
    }

    /**
     * Host listener that handles clicks outside the notification panel.
     * Closes the panel and marks notifications as seen when appropriate.
     *
     * @param target - The element that was clicked
     */
    @HostListener('document:click', ['$event.target'])
    protected onClickOutside(target: any) {
        const clickedInside = this.elementRef.nativeElement.contains(target);
        if (!clickedInside && this.isShown()) {
            this.isShown.set(false);
            this.updateCurrentCategoryNotificationsToSeenOnClient();
        }
    }

    /**
     * Handles when scrolling reaches the bottom of the notification list.
     * Triggers loading of the next page of notifications if available.
     */
    protected onScrollReachBottom() {
        if (this.pagesFinished || this.isLoading()) {
            return;
        }

        this.queryStartSize = this.notificationsForSelectedCategory().length;
        this.queryCurrentCategory();
    }

    /**
     * Initializes the notification presets once both settingInfo and info are available.
     * Sets up the selectable presets and the currently selected preset.
     */
    private initializeCourseNotificationValues() {
        this.selectableSettingPresets.set(this.info!.presets);

        this.selectedSettingPreset.set(
            this.settingInfo!.selectedPreset === 0 ? undefined : this.selectableSettingPresets()!.find((preset) => preset.typeId === this.settingInfo!.selectedPreset)!,
        );
    }

    /**
     * Handles selection of a notification preset.
     *
     * @param presetTypeId - The ID of the selected preset (0 for custom settings)
     */
    protected presetSelected(presetTypeId: number) {
        this.courseNotificationSettingService.setSettingPreset(this.courseId(), presetTypeId, this.selectedSettingPreset());

        this.selectedSettingPreset.set(presetTypeId === 0 ? undefined : this.selectableSettingPresets()!.find((preset) => preset.typeId === presetTypeId)!);
    }

    /**
     * Marks all currently shown notifications (the selected category) as read/seen,
     * both in the local state and on the server.
     */
    protected markAllAsReadClicked() {
        this.updateCurrentCategoryNotificationsToSeenOnServer();
        this.updateCurrentCategoryNotificationsToSeenOnClient();
    }

    /**
     * Handles click on the close button for a notification.
     * Archives the notification and removes it from the display.
     *
     * @param notification - The notification to close
     */
    protected closeClicked(notification: CourseNotification) {
        this.courseNotificationService.setNotificationStatus(notification.courseId!, [notification.notificationId!], CourseNotificationViewingStatus.ARCHIVED);
        this.courseNotificationService.removeNotificationFromMap(notification.courseId!, notification);
    }

    /**
     * Updates notification status to SEEN in the local state.
     * Decreases notification count accordingly.
     *
     * @private
     */
    private updateCurrentCategoryNotificationsToSeenOnClient() {
        // On the client, we want to update the status as soon as the user is done viewing them
        const visibleUnseenNotificationIds = this.getVisibleUnseenNotificationIds();

        if (visibleUnseenNotificationIds.length <= 0) {
            return;
        }

        this.courseNotificationService.setNotificationStatusInMap(this.courseId(), visibleUnseenNotificationIds, CourseNotificationViewingStatus.SEEN);
        this.courseNotificationService.decreaseNotificationCountBy(this.courseId(), visibleUnseenNotificationIds.length);
    }

    /**
     * Updates notification status to SEEN on the server.
     * Called when notifications become visible in the UI.
     *
     * @private
     */
    private updateCurrentCategoryNotificationsToSeenOnServer() {
        // On the server, we always want to update the status as soon as they are loaded
        const visibleUnseenNotificationIds = this.getVisibleUnseenNotificationIds();

        if (visibleUnseenNotificationIds.length <= 0) {
            return;
        }

        this.courseNotificationService.setNotificationStatus(this.courseId(), visibleUnseenNotificationIds, CourseNotificationViewingStatus.SEEN);
    }

    /**
     * Gets IDs of all visible notifications with UNSEEN status.
     *
     * @returns Array of notification IDs
     * @private
     */
    private getVisibleUnseenNotificationIds(): number[] {
        return this.notificationsForSelectedCategory()
            .filter((notification) => {
                return notification.status === CourseNotificationViewingStatus.UNSEEN;
            })
            .map((notification: CourseNotification) => notification.notificationId!);
    }

    /**
     * Filters notifications to match the currently selected category.
     * Updates the notificationsForSelectedCategory array.
     *
     * @private
     */
    private filterNotificationsIntoCurrentCategory() {
        if (this.notifications && this.notifications.length > 0) {
            this.notificationsForSelectedCategory.set(
                this.notifications.filter((notification) => {
                    return notification.category?.valueOf() == this.selectedCategory;
                }),
            );
        } else {
            this.notificationsForSelectedCategory.set([]);
        }
    }

    /**
     * Fetches the next page of notifications for the current category.
     * Manages loading state and scroll position preservation.
     *
     * @private
     */
    private queryCurrentCategory() {
        if (this.pagesFinished) {
            return;
        }

        this.isLoading.set(true);

        this.pagesFinished = !this.courseNotificationService.getNextNotificationPage(this.courseId());

        if (this.pagesFinished) {
            setTimeout(() => {
                this.scrollContainer()!.nativeElement.scrollTop = this.savedScrollPosition;
            });
            this.isLoading.set(false);
        }
    }

    /**
     * Checks if the user has scrolled to the bottom of the notification list.
     *
     * @returns Whether the scroll position is at or near the bottom
     */
    private isScrolledToBottom(): boolean {
        const element = this.scrollContainer()!.nativeElement;
        return Math.round(element.scrollTop + element.clientHeight) >= element.scrollHeight - 20;
    }
}
