import { AfterViewInit, Component, ElementRef, HostListener, OnDestroy, OnInit, inject, input, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { faArchive, faBell, faEnvelopeOpen, faEye, faFilter, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { CourseNotificationBubbleComponent } from 'app/communication/course-notification/course-notification-bubble/course-notification-bubble.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseNotificationCategory } from 'app/entities/course-notification/course-notification-category';
import { CourseNotification } from 'app/entities/course-notification/course-notification';
import { CourseNotificationComponent } from 'app/communication/course-notification/course-notification/course-notification.component';
import { CourseNotificationService } from 'app/communication/course-notification/course-notification.service';
import { Subscription, fromEvent } from 'rxjs';
import { CourseNotificationViewingStatus } from 'app/entities/course-notification/course-notification-viewing-status';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { animate, style, transition, trigger } from '@angular/animations';

@Component({
    selector: 'jhi-course-notification-overview',
    imports: [FontAwesomeModule, CourseNotificationBubbleComponent, CommonModule, TranslateDirective, CourseNotificationComponent],
    templateUrl: './course-notification-overview.component.html',
    styleUrls: ['./course-notification-overview.component.scss'],
    animations: [
        trigger('fadeAnimation', [transition(':enter', [style({ opacity: 0 }), animate('200ms 400ms ease-in-out', style({ opacity: 1 }))])]),
        trigger('notificationWrapAnimation', [
            transition(':enter', [style({ opacity: 0 }), animate('200ms ease-in-out', style({ opacity: 1 }))]),
            transition(':leave', [animate('400ms ease-in-out', style({ opacity: 0 }))]),
        ]),
    ],
})
export class CourseNotificationOverviewComponent implements OnDestroy, OnInit, AfterViewInit {
    readonly courseId = input.required<number>();

    private elementRef = inject(ElementRef);
    private courseNotificationService = inject(CourseNotificationService);

    // Icons
    protected readonly faArchive = faArchive;
    protected readonly faBell = faBell;
    protected readonly faEye = faEye;
    protected readonly faFilter = faFilter;
    protected readonly faEnvelopeOpen = faEnvelopeOpen;
    protected readonly faSpinner = faSpinner;

    protected courseCategories: string[];

    protected isShown = false;
    protected selectedCategory = CourseNotificationCategory.COMMUNICATION;
    protected notifications: CourseNotification[];
    protected notificationsForSelectedCategory: CourseNotification[] = [];
    protected courseNotificationCount: number = 0;
    protected queryStartSize: number = 0;
    protected queryCount: number = 1;
    protected savedScrollPosition: number = 0;
    protected pagesFinished: boolean = false;
    protected isLoading: boolean = false;
    private courseNotificationCountSubscription: Subscription | null = null;
    private courseNotificationSubscription: Subscription | null = null;
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
        if (this.courseNotificationCountSubscription !== null) {
            this.courseNotificationCountSubscription.unsubscribe();
        }
        if (this.courseNotificationSubscription !== null) {
            this.courseNotificationSubscription.unsubscribe();
        }
    }

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

    protected isCategorySelected(categoryString: string) {
        return CourseNotificationCategory[categoryString as keyof typeof CourseNotificationCategory] == this.selectedCategory;
    }

    protected selectCategory(categoryString: string) {
        this.updateCurrentCategoryNotificationsToSeenOnClient();

        this.selectedCategory = CourseNotificationCategory[categoryString as keyof typeof CourseNotificationCategory];

        this.filterNotificationsIntoCurrentCategory();

        this.updateCurrentCategoryNotificationsToSeenOnServer();

        if (!this.pagesFinished && this.notificationsForSelectedCategory.length < this.courseNotificationService.pageSize) {
            this.queryCurrentCategory();
        }
    }

    @HostListener('document:click', ['$event.target'])
    protected onClickOutside(target: any) {
        const clickedInside = this.elementRef.nativeElement.contains(target);
        if (!clickedInside && this.isShown) {
            this.isShown = false;
            this.updateCurrentCategoryNotificationsToSeenOnClient();
        }
    }

    protected onScrollReachBottom() {
        if (this.pagesFinished || this.isLoading) {
            return;
        }

        this.queryStartSize = this.notificationsForSelectedCategory.length;
        this.queryCurrentCategory();
    }

    protected archiveClicked() {
        this.courseNotificationService.archiveAll(this.courseId());
        this.courseNotificationService.archiveAllInMap(this.courseId());
    }

    protected closeClicked(notification: CourseNotification) {
        this.courseNotificationService.setNotificationStatus(notification.courseId!, [notification.notificationId!], CourseNotificationViewingStatus.ARCHIVED);
        this.courseNotificationService.removeNotificationFromMap(notification.courseId!, notification);
    }

    private updateCurrentCategoryNotificationsToSeenOnClient() {
        // On the client, we want to update the status as soon as the user is done viewing them
        const visibleUnseenNotificationIds = this.getVisibleUnseenNotificationIds();

        if (visibleUnseenNotificationIds.length <= 0) {
            return;
        }

        this.courseNotificationService.setNotificationStatusInMap(this.courseId(), visibleUnseenNotificationIds, CourseNotificationViewingStatus.SEEN);
        this.courseNotificationService.decreaseNotificationCountBy(this.courseId(), visibleUnseenNotificationIds.length);
    }

    private updateCurrentCategoryNotificationsToSeenOnServer() {
        // On the server, we always want to update the status as soon as they are loaded
        const visibleUnseenNotificationIds = this.getVisibleUnseenNotificationIds();

        if (visibleUnseenNotificationIds.length <= 0) {
            return;
        }

        this.courseNotificationService.setNotificationStatus(this.courseId(), visibleUnseenNotificationIds, CourseNotificationViewingStatus.SEEN);
    }

    private getVisibleUnseenNotificationIds(): number[] {
        return this.notificationsForSelectedCategory
            .filter((notification) => {
                return notification.status === CourseNotificationViewingStatus.UNSEEN;
            })
            .map((notification: CourseNotification) => notification.notificationId!);
    }

    private filterNotificationsIntoCurrentCategory() {
        if (this.notifications && this.notifications.length > 0) {
            this.notificationsForSelectedCategory = this.notifications.filter((notification) => {
                return notification.category?.valueOf() == this.selectedCategory;
            });
        } else {
            this.notificationsForSelectedCategory = [];
        }
    }

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

    private isScrolledToBottom(): boolean {
        const element = this.scrollContainer()!.nativeElement;
        return Math.round(element.scrollTop + element.clientHeight) >= element.scrollHeight - 20;
    }
}
