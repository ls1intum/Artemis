import { AfterViewInit, Component, DestroyRef, ElementRef, HostListener, inject, input, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { faBell, faCog, faEnvelopeOpen, faFilter, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { CourseNotificationBubbleComponent } from 'app/notification/course-notification/course-notification-bubble/course-notification-bubble.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { CourseNotificationCategory } from 'app/notification/shared/entities/course-notification/course-notification-category';
import { CourseNotification } from 'app/notification/shared/entities/course-notification/course-notification';
import { CourseNotificationComponent } from 'app/notification/course-notification/course-notification/course-notification.component';
import { CourseNotificationService } from 'app/notification/course-notification/course-notification.service';
import { AccountService } from 'app/core/auth/account.service';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { firstValueFrom, from, fromEvent } from 'rxjs';
import { CourseNotificationViewingStatus } from 'app/notification/shared/entities/course-notification/course-notification-viewing-status';
import { debounceTime, distinctUntilChanged, filter, switchMap, tap } from 'rxjs/operators';
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
export class CourseNotificationOverviewComponent implements AfterViewInit {
    readonly courseId = input.required<number>();

    private elementRef = inject(ElementRef);
    private courseNotificationService = inject(CourseNotificationService);
    private accountService = inject(AccountService);
    private courseStorageService = inject(CourseStorageService);
    private courseNotificationSettingService = inject(CourseNotificationSettingService);
    private destroyRef = inject(DestroyRef);

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
    protected notifications?: CourseNotification[];
    protected readonly notificationsForSelectedCategory = signal<CourseNotification[]>([]);
    protected readonly courseNotificationCount = signal<number>(0);
    protected queryStartSize: number = 0;
    protected queryCount: number = 1;
    protected savedScrollPosition: number = 0;
    protected pagesFinished: boolean = false;
    protected readonly isLoading = signal<boolean>(false);
    private scrollContainer = viewChild<ElementRef>('scrollContainer');

    protected readonly CourseNotificationViewingStatus = CourseNotificationViewingStatus;

    constructor() {
        this.courseCategories.set(Object.keys(CourseNotificationCategory).filter((category) => isNaN(Number(category))));

        this.subscribeToSettingAndInfoChanges();
        this.subscribeToNotificationChanges();
    }

    /**
     * Whether the IRIS_REVIEW tab should be shown. Hidden for students because they have nothing to
     * review. The decision is made when ngOnInit runs (the courseId input is required, so it is set).
     */
    protected isCategoryVisible(categoryString: string): boolean {
        if (categoryString !== 'IRIS_REVIEW') {
            return true;
        }
        const course = this.courseStorageService.getCourse(this.courseId());
        return !!course && this.accountService.isAtLeastTutorInCourse(course);
    }

    ngAfterViewInit(): void {
        if (!this.scrollContainer()) {
            return;
        }

        fromEvent(this.scrollContainer()!.nativeElement, 'scroll')
            .pipe(debounceTime(200), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
            .subscribe(() => {
                if (this.isScrolledToBottom()) {
                    this.savedScrollPosition = this.scrollContainer()!.nativeElement.scrollTop;
                    this.onScrollReachBottom();
                }
            });
    }

    /**
     * Reacts to course changes by (re-)fetching the notification info and setting info for the new course.
     * Uses `switchMap` so an in-flight request for a previous course is cancelled once the course changes,
     * and `takeUntilDestroyed` so the subscription is cleaned up automatically when the component is destroyed.
     */
    private subscribeToSettingAndInfoChanges(): void {
        toObservable(this.courseId)
            .pipe(
                tap(() => {
                    this.settingInfo = undefined;
                    this.info = undefined;
                    this.selectableSettingPresets.set(undefined);
                    this.selectedSettingPreset.set(undefined);
                }),
                switchMap((courseId) =>
                    from(
                        Promise.all([
                            firstValueFrom(this.courseNotificationService.getInfo()),
                            firstValueFrom(
                                this.courseNotificationSettingService
                                    .getSettingInfo(courseId, false)
                                    .pipe(filter((value): value is CourseNotificationSettingInfo => value !== undefined)),
                            ),
                        ]).then(([info, settingInfo]) => ({ info, settingInfo })),
                    ),
                ),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe(({ info, settingInfo }) => {
                if (info.body) {
                    this.info = info.body;
                }
                this.settingInfo = settingInfo;
                if (this.info && this.settingInfo) {
                    this.initializeCourseNotificationValues();
                }
            });
    }

    /**
     * Reacts to course changes by (re-)fetching the notification count and the notification list for the new
     * course. Uses `switchMap` so a course change automatically cancels the previous course's subscriptions,
     * and `takeUntilDestroyed` so the subscription is cleaned up automatically when the component is destroyed.
     */
    private subscribeToNotificationChanges(): void {
        toObservable(this.courseId)
            .pipe(
                tap(() => this.resetNotificationQueryState()),
                switchMap((courseId) => this.courseNotificationService.getNotificationCountForCourse$(courseId)),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe((count) => this.courseNotificationCount.set(count));

        toObservable(this.courseId)
            .pipe(
                switchMap((courseId) => this.courseNotificationService.getNotificationsForCourse$(courseId)),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe((notifications) => this.handleNotificationsUpdate(notifications));
    }

    /**
     * Resets the local pagination and notification state. Called whenever the course changes so that
     * stale data from the previous course is not shown while the new course's data is being fetched.
     */
    private resetNotificationQueryState(): void {
        this.notifications = undefined;
        this.notificationsForSelectedCategory.set([]);
        this.queryStartSize = 0;
        this.queryCount = 1;
        this.pagesFinished = false;
        this.savedScrollPosition = 0;
        this.isLoading.set(false);
        this.courseNotificationCount.set(0);
    }

    /**
     * Processes a fresh list of notifications for the current course: filters them into the selected
     * category and, if server-side categorization paging has not yet delivered enough items, triggers
     * additional page fetches (temporary solution until server-side categorization paging is possible).
     *
     * @param notifications - The up-to-date list of notifications for the current course
     */
    private handleNotificationsUpdate(notifications: CourseNotification[]): void {
        // Decide which scroll position to restore after the list re-renders:
        // - Pagination update (`isLoading` was set by queryCurrentCategory): the list is currently replaced by the
        //   loading spinner, which clamps the container's scrollTop to 0, so the live value is unusable. Restore
        //   `savedScrollPosition` (captured when pagination was triggered) so infinite scroll stays in place.
        // - Non-pagination update (e.g. closing/removing a single notification): `savedScrollPosition` is stale (it
        //   only tracks the bottom), so keep the live position - otherwise the list would jump to the bottom.
        const isPaginationUpdate = this.isLoading();
        const currentScrollTop = this.scrollContainer()?.nativeElement.scrollTop ?? 0;

        this.notifications = notifications;

        this.filterNotificationsIntoCurrentCategory();

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
                const targetScrollTop = isPaginationUpdate ? this.savedScrollPosition : currentScrollTop;
                setTimeout(() => {
                    this.scrollContainer()!.nativeElement.scrollTop = targetScrollTop;
                });
                this.updateCurrentCategoryNotificationsToSeenOnServer();
            }
        }
    }

    /**
     * Closes the notification overlay and marks visible notifications as seen on the client.
     */
    protected closeOverlay() {
        if (this.isShown()) {
            this.isShown.set(false);
            this.updateCurrentCategoryNotificationsToSeenOnClient();
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
    protected onClickOutside(target: EventTarget | null) {
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
        const newPreset = presetTypeId === 0 ? undefined : this.selectableSettingPresets()!.find((preset) => preset.typeId === presetTypeId);

        this.courseNotificationSettingService.setSettingPreset(this.courseId(), presetTypeId, newPreset);

        this.selectedSettingPreset.set(newPreset);
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
