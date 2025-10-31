import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { CourseNotificationOverviewComponent } from 'app/communication/course-notification/course-notification-overview/course-notification-overview.component';
import { CourseNotificationService } from 'app/communication/course-notification/course-notification.service';
import { CourseNotification } from 'app/communication/shared/entities/course-notification/course-notification';
import { CourseNotificationCategory } from 'app/communication/shared/entities/course-notification/course-notification-category';
import { CourseNotificationViewingStatus } from 'app/communication/shared/entities/course-notification/course-notification-viewing-status';
import { Subject } from 'rxjs';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective } from 'ng-mocks';
import { CourseNotificationBubbleComponent } from 'app/communication/course-notification/course-notification-bubble/course-notification-bubble.component';
import { CourseNotificationComponent } from 'app/communication/course-notification/course-notification/course-notification.component';
import { CommonModule } from '@angular/common';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute } from '@angular/router';

describe('CourseNotificationOverviewComponent', () => {
    let component: CourseNotificationOverviewComponent;
    let fixture: ComponentFixture<CourseNotificationOverviewComponent>;
    let courseNotificationService: CourseNotificationService;
    let notificationCountSubject: Subject<number>;
    let notificationsSubject: Subject<CourseNotification[]>;
    let componentAsAny: any;

    const createMockNotification = (
        id: number,
        courseId: number,
        category: CourseNotificationCategory,
        status: CourseNotificationViewingStatus = CourseNotificationViewingStatus.UNSEEN,
    ): CourseNotification => {
        return new CourseNotification(id, courseId, 'newPostNotification', category, status, dayjs(), { courseTitle: 'Test Course', courseIconUrl: 'test-icon-url' }, '/');
    };

    beforeEach(async () => {
        notificationCountSubject = new Subject<number>();
        notificationsSubject = new Subject<CourseNotification[]>();

        courseNotificationService = {
            getNotificationCountForCourse$: jest.fn().mockReturnValue(notificationCountSubject.asObservable()),
            getNotificationsForCourse$: jest.fn().mockReturnValue(notificationsSubject.asObservable()),
            setNotificationStatus: jest.fn(),
            setNotificationStatusInMap: jest.fn(),
            decreaseNotificationCountBy: jest.fn(),
            archiveAll: jest.fn(),
            archiveAllInMap: jest.fn(),
            removeNotificationFromMap: jest.fn(),
            getNextNotificationPage: jest.fn().mockReturnValue(true),
            pageSize: 10,
        } as unknown as CourseNotificationService;

        await TestBed.configureTestingModule({
            imports: [CommonModule, FontAwesomeModule],
            declarations: [
                CourseNotificationOverviewComponent,
                MockComponent(CourseNotificationBubbleComponent),
                MockComponent(CourseNotificationComponent),
                MockDirective(TranslateDirective),
            ],
            providers: [
                { provide: CourseNotificationService, useValue: courseNotificationService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseNotificationOverviewComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('courseId', 101);

        fixture.detectChanges();
        componentAsAny = component as any;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should initialize with the correct default values', () => {
        expect(componentAsAny.isShown).toBeFalse();
        expect(componentAsAny.selectedCategory).toBe(CourseNotificationCategory.GENERAL);
        expect(componentAsAny.notifications).toBeUndefined();
        expect(componentAsAny.notificationsForSelectedCategory).toEqual([]);
        expect(componentAsAny.courseNotificationCount).toBe(0);
        expect(componentAsAny.pagesFinished).toBeFalse();
        expect(componentAsAny.isLoading).toBeFalse();
    });

    it('should set up notification count subscription on init', () => {
        expect(courseNotificationService.getNotificationCountForCourse$).toHaveBeenCalledWith(101);

        notificationCountSubject.next(5);
        expect(componentAsAny.courseNotificationCount).toBe(5);
    });

    it('should set up notifications subscription on init', () => {
        expect(courseNotificationService.getNotificationsForCourse$).toHaveBeenCalledWith(101);
    });

    it('should filter notifications by selected category', () => {
        const communicationNotification = createMockNotification(1, 101, CourseNotificationCategory.COMMUNICATION);
        const generalNotification = createMockNotification(2, 101, CourseNotificationCategory.GENERAL);
        componentAsAny.notifications = [communicationNotification, generalNotification];

        componentAsAny.filterNotificationsIntoCurrentCategory();

        expect(componentAsAny.notificationsForSelectedCategory).toHaveLength(1);
        expect(componentAsAny.notificationsForSelectedCategory[0]).toBe(generalNotification);
    });

    it('should toggle overlay visibility when toggleOverlay is called', () => {
        componentAsAny.isShown = false;
        const updateSpy = jest.spyOn(component as any, 'updateCurrentCategoryNotificationsToSeenOnClient');

        componentAsAny.toggleOverlay();

        expect(componentAsAny.isShown).toBeTrue();
        expect(updateSpy).not.toHaveBeenCalled();

        componentAsAny.toggleOverlay();

        expect(componentAsAny.isShown).toBeFalse();
        expect(updateSpy).toHaveBeenCalledOnce();
    });

    it('should query for more notifications if less than pageSize are available', () => {
        componentAsAny.notificationsForSelectedCategory = Array(5)
            .fill(null)
            .map((_, i) => createMockNotification(i, 101, CourseNotificationCategory.COMMUNICATION));
        componentAsAny.pagesFinished = false;
        const querySpy = jest.spyOn(component as any, 'queryCurrentCategory');

        componentAsAny.toggleOverlay();

        expect(componentAsAny.queryStartSize).toBe(5);
        expect(querySpy).toHaveBeenCalledOnce();
    });

    it('should correctly check if a category is selected', () => {
        componentAsAny.selectedCategory = CourseNotificationCategory.COMMUNICATION;

        expect(componentAsAny.isCategorySelected('COMMUNICATION')).toBeTrue();
        expect(componentAsAny.isCategorySelected('GENERAL')).toBeFalse();
    });

    it('should change selected category and update notifications', () => {
        const updateClientSpy = jest.spyOn(component as any, 'updateCurrentCategoryNotificationsToSeenOnClient');
        const updateServerSpy = jest.spyOn(component as any, 'updateCurrentCategoryNotificationsToSeenOnServer');
        const filterSpy = jest.spyOn(component as any, 'filterNotificationsIntoCurrentCategory');

        componentAsAny.selectCategory('GENERAL');

        expect(componentAsAny.selectedCategory).toBe(CourseNotificationCategory.GENERAL);
        expect(updateClientSpy).toHaveBeenCalledOnce();
        expect(filterSpy).toHaveBeenCalledOnce();
        expect(updateServerSpy).toHaveBeenCalledOnce();
    });

    it('should query for more notifications when changing to a category with fewer than pageSize notifications', () => {
        componentAsAny.notificationsForSelectedCategory = Array(5)
            .fill(null)
            .map((_, i) => createMockNotification(i, 101, CourseNotificationCategory.COMMUNICATION));
        componentAsAny.pagesFinished = false;
        const querySpy = jest.spyOn(component as any, 'queryCurrentCategory');

        componentAsAny.selectCategory('GENERAL');

        expect(querySpy).toHaveBeenCalledOnce();
    });

    it('should hide overlay when clicking outside the component', () => {
        componentAsAny.isShown = true;
        const updateSpy = jest.spyOn(component as any, 'updateCurrentCategoryNotificationsToSeenOnClient');
        const elementContainsSpy = jest.spyOn(componentAsAny.elementRef.nativeElement, 'contains').mockReturnValue(false);

        componentAsAny.onClickOutside({});

        expect(componentAsAny.isShown).toBeFalse();
        expect(updateSpy).toHaveBeenCalledOnce();
        expect(elementContainsSpy).toHaveBeenCalledOnce();
    });

    it('should not hide overlay when clicking inside the component', () => {
        componentAsAny.isShown = true;
        const updateSpy = jest.spyOn(component as any, 'updateCurrentCategoryNotificationsToSeenOnClient');
        const elementContainsSpy = jest.spyOn(componentAsAny.elementRef.nativeElement, 'contains').mockReturnValue(true);

        componentAsAny.onClickOutside({});

        expect(componentAsAny.isShown).toBeTrue();
        expect(updateSpy).not.toHaveBeenCalled();
        expect(elementContainsSpy).toHaveBeenCalledOnce();
    });

    it('should load more notifications when scrolling to bottom', () => {
        componentAsAny.pagesFinished = false;
        componentAsAny.isLoading = false;
        const querySpy = jest.spyOn(component as any, 'queryCurrentCategory');

        componentAsAny.onScrollReachBottom();

        expect(componentAsAny.isLoading).toBeTrue();
        expect(querySpy).toHaveBeenCalledOnce();
    });

    it('should not load more notifications when already loading or finished', () => {
        componentAsAny.pagesFinished = false;
        componentAsAny.isLoading = true;
        const querySpy = jest.spyOn(component as any, 'queryCurrentCategory');

        componentAsAny.onScrollReachBottom();

        expect(querySpy).not.toHaveBeenCalled();

        componentAsAny.pagesFinished = true;
        componentAsAny.isLoading = false;

        componentAsAny.onScrollReachBottom();

        expect(querySpy).not.toHaveBeenCalled();
    });

    it('should handle archive button click', () => {
        componentAsAny.archiveClicked();

        expect(courseNotificationService.archiveAll).toHaveBeenCalledWith(101);
        expect(courseNotificationService.archiveAllInMap).toHaveBeenCalledWith(101);
    });

    it('should handle notification close button click', () => {
        const notification = createMockNotification(1, 101, CourseNotificationCategory.COMMUNICATION);

        componentAsAny.closeClicked(notification);

        expect(courseNotificationService.setNotificationStatus).toHaveBeenCalledWith(101, [1], CourseNotificationViewingStatus.ARCHIVED);
        expect(courseNotificationService.removeNotificationFromMap).toHaveBeenCalledWith(101, notification);
    });

    it('should update unseen notifications to seen on client side', () => {
        const unseenNotification1 = createMockNotification(1, 101, CourseNotificationCategory.COMMUNICATION);
        const unseenNotification2 = createMockNotification(2, 101, CourseNotificationCategory.COMMUNICATION);
        const seenNotification = createMockNotification(3, 101, CourseNotificationCategory.COMMUNICATION, CourseNotificationViewingStatus.SEEN);

        componentAsAny.notificationsForSelectedCategory = [unseenNotification1, unseenNotification2, seenNotification];

        componentAsAny.updateCurrentCategoryNotificationsToSeenOnClient();

        expect(courseNotificationService.setNotificationStatusInMap).toHaveBeenCalledWith(101, [1, 2], CourseNotificationViewingStatus.SEEN);
        expect(courseNotificationService.decreaseNotificationCountBy).toHaveBeenCalledWith(101, 2);
    });

    it('should update unseen notifications to seen on server side', () => {
        const unseenNotification1 = createMockNotification(1, 101, CourseNotificationCategory.COMMUNICATION);
        const unseenNotification2 = createMockNotification(2, 101, CourseNotificationCategory.COMMUNICATION);
        const seenNotification = createMockNotification(3, 101, CourseNotificationCategory.COMMUNICATION, CourseNotificationViewingStatus.SEEN);

        componentAsAny.notificationsForSelectedCategory = [unseenNotification1, unseenNotification2, seenNotification];

        componentAsAny.updateCurrentCategoryNotificationsToSeenOnServer();

        expect(courseNotificationService.setNotificationStatus).toHaveBeenCalledWith(101, [1, 2], CourseNotificationViewingStatus.SEEN);
    });

    it('should correctly identify visible unseen notification IDs', () => {
        const unseenNotification1 = createMockNotification(1, 101, CourseNotificationCategory.COMMUNICATION);
        const unseenNotification2 = createMockNotification(2, 101, CourseNotificationCategory.COMMUNICATION);
        const seenNotification = createMockNotification(3, 101, CourseNotificationCategory.COMMUNICATION, CourseNotificationViewingStatus.SEEN);

        componentAsAny.notificationsForSelectedCategory = [unseenNotification1, unseenNotification2, seenNotification];

        const result = componentAsAny.getVisibleUnseenNotificationIds();

        expect(result).toEqual([1, 2]);
    });

    it('should not update notifications if no unseen notifications exist', () => {
        const seenNotification = createMockNotification(3, 101, CourseNotificationCategory.COMMUNICATION, CourseNotificationViewingStatus.SEEN);
        componentAsAny.notificationsForSelectedCategory = [seenNotification];

        componentAsAny.updateCurrentCategoryNotificationsToSeenOnClient();
        componentAsAny.updateCurrentCategoryNotificationsToSeenOnServer();

        expect(courseNotificationService.setNotificationStatusInMap).not.toHaveBeenCalled();
        expect(courseNotificationService.decreaseNotificationCountBy).not.toHaveBeenCalled();
        expect(courseNotificationService.setNotificationStatus).not.toHaveBeenCalled();
    });

    it('should query for more notifications from service', () => {
        componentAsAny.pagesFinished = false;

        componentAsAny.queryCurrentCategory();

        expect(componentAsAny.isLoading).toBeTrue();
        expect(courseNotificationService.getNextNotificationPage).toHaveBeenCalledWith(101);
    });

    it('should set pagesFinished when service returns false', () => {
        componentAsAny.pagesFinished = false;
        jest.spyOn(courseNotificationService, 'getNextNotificationPage').mockReturnValue(false);

        componentAsAny.queryCurrentCategory();

        expect(componentAsAny.pagesFinished).toBeTrue();
        expect(componentAsAny.isLoading).toBeFalse();
    });

    it('should properly clean up subscriptions on destroy', () => {
        const countUnsubscribeSpy = jest.fn();
        const notificationsUnsubscribeSpy = jest.fn();

        componentAsAny.courseNotificationCountSubscription = {
            unsubscribe: countUnsubscribeSpy,
        };

        componentAsAny.courseNotificationSubscription = {
            unsubscribe: notificationsUnsubscribeSpy,
        };

        component.ngOnDestroy();

        expect(countUnsubscribeSpy).toHaveBeenCalledOnce();
        expect(notificationsUnsubscribeSpy).toHaveBeenCalledOnce();
    });

    it('should toggle overlay when button is clicked', () => {
        const toggleSpy = jest.spyOn(component as any, 'toggleOverlay');
        const button = fixture.debugElement.query(By.css('button'));

        button.nativeElement.click();
        fixture.detectChanges();

        expect(toggleSpy).toHaveBeenCalledOnce();
    });

    it('should apply is-shown class when overlay is shown', () => {
        componentAsAny.isShown = true;

        fixture.detectChanges();

        const overlay = fixture.debugElement.query(By.css('.course-notification-overview-overlay'));
        expect(overlay.classes['is-shown']).toBeTrue();
    });

    it('should display loading indicator when isLoading is true', () => {
        componentAsAny.isLoading = true;

        fixture.detectChanges();

        const loadingIndicator = fixture.debugElement.query(By.css('.course-notification-overview-notification-loading'));
        expect(loadingIndicator).not.toBeNull();
    });

    it('should display empty state when no notifications are available', () => {
        componentAsAny.isLoading = false;
        componentAsAny.notificationsForSelectedCategory = [];

        fixture.detectChanges();

        const emptyState = fixture.debugElement.query(By.css('.course-notification-overview-notification-empty-prompt'));
        expect(emptyState).not.toBeNull();
    });

    it('should display notifications when available', () => {
        componentAsAny.isLoading = false;
        componentAsAny.notificationsForSelectedCategory = [createMockNotification(1, 101, CourseNotificationCategory.COMMUNICATION)];

        fixture.detectChanges();

        const notificationElements = fixture.debugElement.queryAll(By.css('jhi-course-notification'));
        expect(notificationElements).toHaveLength(1);
    });

    it('should display correct categories and handle category selection', () => {
        fixture.detectChanges();

        const categoryElements = fixture.debugElement.queryAll(By.css('.course-notification-overview-category'));
        expect(categoryElements.length).toBeGreaterThan(0);

        const selectCategorySpy = jest.spyOn(component as any, 'selectCategory');

        categoryElements[0].nativeElement.click();

        expect(selectCategorySpy).toHaveBeenCalled();
    });
});
