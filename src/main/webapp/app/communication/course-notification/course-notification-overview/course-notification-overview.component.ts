import { AfterViewInit, Component, ElementRef, HostListener, OnDestroy, OnInit, inject, input, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { faBell, faCog, faEnvelopeOpen, faFilter, faSpinner, faTrash } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { CourseNotificationBubbleComponent } from 'app/communication/course-notification/course-notification-bubble/course-notification-bubble.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseNotificationCategory } from 'app/communication/shared/entities/course-notification/course-notification-category';
import { CourseNotification } from 'app/communication/shared/entities/course-notification/course-notification';
import { CourseNotificationComponent } from 'app/communication/course-notification/course-notification/course-notification.component';
import { CourseNotificationService } from 'app/communication/course-notification/course-notification.service';
import { Subscription, fromEvent } from 'rxjs';
import { CourseNotificationViewingStatus } from 'app/communication/shared/entities/course-notification/course-notification-viewing-status';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { RouterLink } from '@angular/router';

/**
 * Component that displays a comprehensive overview of course notifications.
 * Features a dropdown interface with category filtering, infinite scrolling,
 * and notification status management.
 */
@Component({
    selector: 'jhi-course-notification-overview',
    imports: [FontAwesomeModule, CourseNotificationBubbleComponent, CommonModule, TranslateDirective, CourseNotificationComponent, ArtemisTranslatePipe, NgbTooltip, RouterLink],
    templateUrl: './course-notification-overview.component.html',
    styleUrls: ['./course-notification-overview.component.scss'],
})
export class CourseNotificationOverviewComponent implements OnDestroy, OnInit, AfterViewInit {
    readonly courseId = input.required<number>();

    private elementRef = inject(ElementRef);
    private courseNotificationService = inject(CourseNotificationService);

    // Icons
    protected readonly faTrash = faTrash;
    protected readonly faBell = faBell;
    protected readonly faCog = faCog;
    protected readonly faFilter = faFilter;
    protected readonly faEnvelopeOpen = faEnvelopeOpen;
    protected readonly faSpinner = faSpinner;

    protected courseCategories: string[];

    protected isShown = false;
    protected selectedCategory = CourseNotificationCategory.GENERAL;
    protected notifications: CourseNotification[];
    protected notificationsForSelectedCategory: CourseNotification[] = [];
    protected courseNotificationCount: number = 0;
    protected queryStartSize: number = 0;
    protected queryCount: number = 1;
    protected savedScrollPosition: number = 0;
    protected pagesFinished: boolean = false;
    protected isLoading: boolean = false;
    private courseNotificationCountSubscription?: Subscription;
    private courseNotificationSubscription?: Subscription;
    private scrollContainer = viewChild<ElementRef>('scrollContainer');

    protected readonly CourseNotificationViewingStatus = CourseNotificationViewingStatus;

    constructor() {
        this.courseCategories = Object.keys(CourseNotificationCategory).filter((category) => isNaN(Number(category)));
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
            this.courseNotificationCount = count;
        });
        this.courseNotificationSubscription = this.courseNotificationService.getNotificationsForCourse$(this.courseId()).subscribe((notifications) => {
            this.notifications = notifications;

            this.filterNotificationsIntoCurrentCategory();

            // Note: This is a temporary solution until server-side categorization paging is possible
            if (
                this.isLoading &&
                !this.pagesFinished &&
                this.queryCount <= 3 &&
                this.notificationsForSelectedCategory.length < this.queryStartSize + this.courseNotificationService.pageSize
            ) {
                this.queryCount++;
                this.queryCurrentCategory();
            } else {
                this.isLoading = false;
                this.queryCount = 1;

                if (this.isShown) {
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
        this.isShown = !this.isShown;

        if (!this.isShown) {
            this.updateCurrentCategoryNotificationsToSeenOnClient();
        }

        if (this.notificationsForSelectedCategory.length < this.courseNotificationService.pageSize && !this.pagesFinished) {
            this.queryStartSize = this.notificationsForSelectedCategory.length;
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

        if (!this.pagesFinished && this.notificationsForSelectedCategory.length < this.courseNotificationService.pageSize) {
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
        if (!clickedInside && this.isShown) {
            this.isShown = false;
            this.updateCurrentCategoryNotificationsToSeenOnClient();
        }
    }

    /**
     * Handles when scrolling reaches the bottom of the notification list.
     * Triggers loading of the next page of notifications if available.
     */
    protected onScrollReachBottom() {
        if (this.pagesFinished || this.isLoading) {
            return;
        }

        this.queryStartSize = this.notificationsForSelectedCategory.length;
        this.queryCurrentCategory();
    }

    /**
     * Handles click on the archive button.
     * Archives all notifications for the course both on server and in local state.
     */
    protected archiveClicked() {
        this.courseNotificationService.archiveAll(this.courseId());
        this.courseNotificationService.archiveAllInMap(this.courseId());
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
        return this.notificationsForSelectedCategory
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
            this.notificationsForSelectedCategory = this.notifications.filter((notification) => {
                return notification.category?.valueOf() == this.selectedCategory;
            });
        } else {
            this.notificationsForSelectedCategory = [];
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

        this.isLoading = true;

        this.pagesFinished = !this.courseNotificationService.getNextNotificationPage(this.courseId());

        if (this.pagesFinished) {
            setTimeout(() => {
                this.scrollContainer()!.nativeElement.scrollTop = this.savedScrollPosition;
            });
            this.isLoading = false;
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
