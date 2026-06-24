import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { CourseNotificationOverviewComponent } from 'app/notification/course-notification/course-notification-overview/course-notification-overview.component';
import { CourseNotificationService } from 'app/notification/course-notification/course-notification.service';
import { CourseNotification } from 'app/notification/shared/entities/course-notification/course-notification';
import { CourseNotificationCategory } from 'app/notification/shared/entities/course-notification/course-notification-category';
import { CourseNotificationViewingStatus } from 'app/notification/shared/entities/course-notification/course-notification-viewing-status';
import { Subject, of } from 'rxjs';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective } from 'ng-mocks';
import { faBell } from '@fortawesome/free-solid-svg-icons';
import { CourseNotificationBubbleComponent } from 'app/notification/course-notification/course-notification-bubble/course-notification-bubble.component';
import { CourseNotificationComponent } from 'app/notification/course-notification/course-notification/course-notification.component';
import { CommonModule } from '@angular/common';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { CourseNotificationSettingService } from 'app/notification/course-notification/course-notification-setting.service';
import { CourseNotificationSettingPreset } from 'app/notification/shared/entities/course-notification/course-notification-setting-preset';
import { CourseNotificationInfo } from 'app/notification/shared/entities/course-notification/course-notification-info';
import { CourseNotificationSettingInfo } from 'app/notification/shared/entities/course-notification/course-notification-setting-info';
import { CourseNotificationPresetPickerComponent } from 'app/notification/course-notification/course-notification-preset-picker/course-notification-preset-picker.component';

describe('CourseNotificationOverviewComponent', () => {
    setupTestBed({ zoneless: true });

    let component: CourseNotificationOverviewComponent;
    let fixture: ComponentFixture<CourseNotificationOverviewComponent>;
    let courseNotificationService: CourseNotificationService;
    let courseNotificationSettingService: CourseNotificationSettingService;
    let notificationCountSubject: Subject<number>;
    let notificationsSubject: Subject<CourseNotification[]>;
    let componentAsAny: any;

    const mockNotificationSettingPresets: CourseNotificationSettingPreset[] = [
        { typeId: 1, identifier: 'All Notifications', presetMap: { test: { PUSH: true, EMAIL: true, WEBAPP: true } } },
        { typeId: 2, identifier: 'Important Only', presetMap: { test: { PUSH: true, EMAIL: false, WEBAPP: true } } },
        { typeId: 3, identifier: 'Minimal', presetMap: { test: { PUSH: false, EMAIL: false, WEBAPP: true } } },
    ];

    const mockSettingInfo: CourseNotificationSettingInfo = {
        selectedPreset: 1,
        notificationTypeChannels: { test: { PUSH: true, EMAIL: true, WEBAPP: true } },
    };

    const mockNotificationInfo: CourseNotificationInfo = {
        presets: mockNotificationSettingPresets,
        notificationTypes: {},
    };

    const createMockNotification = (
        id: number,
        courseId: number,
        category: CourseNotificationCategory,
        status: CourseNotificationViewingStatus = CourseNotificationViewingStatus.UNSEEN,
    ): CourseNotification => {
        return new CourseNotification(id, courseId, 'newPostNotification', category, status, dayjs(), { courseTitle: 'Test Course', courseIconUrl: 'test-icon-url' }, '/');
    };

    afterEach(() => {
        vi.restoreAllMocks();
    });

    beforeEach(async () => {
        notificationCountSubject = new Subject<number>();
        notificationsSubject = new Subject<CourseNotification[]>();

        courseNotificationService = {
            getNotificationCountForCourse$: vi.fn().mockReturnValue(notificationCountSubject.asObservable()),
            getNotificationsForCourse$: vi.fn().mockReturnValue(notificationsSubject.asObservable()),
            setNotificationStatus: vi.fn(),
            setNotificationStatusInMap: vi.fn(),
            decreaseNotificationCountBy: vi.fn(),
            removeNotificationFromMap: vi.fn(),
            getNextNotificationPage: vi.fn().mockReturnValue(true),
            getIconFromType: vi.fn().mockReturnValue(faBell),
            getDateTranslationKey: vi.fn().mockReturnValue(''),
            getDateTranslationParams: vi.fn().mockReturnValue({}),
            safeHtmlForPostingMarkdown: vi.fn().mockReturnValue(''),
            getInfo: vi.fn().mockReturnValue(of(new HttpResponse({ body: mockNotificationInfo }))),
            pageSize: 10,
        } as unknown as CourseNotificationService;

        courseNotificationSettingService = {
            getSettingInfo: vi.fn().mockReturnValue(of(mockSettingInfo)),
            setSettingPreset: vi.fn(),
        } as unknown as CourseNotificationSettingService;

        await TestBed.configureTestingModule({
            imports: [
                CommonModule,
                FontAwesomeModule,
                CourseNotificationOverviewComponent,
                MockComponent(CourseNotificationBubbleComponent),
                MockComponent(CourseNotificationComponent),
                MockDirective(TranslateDirective),
            ],
            providers: [
                { provide: CourseNotificationService, useValue: courseNotificationService },
                { provide: CourseNotificationSettingService, useValue: courseNotificationSettingService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
            ],
        });
        TestBed.overrideComponent(CourseNotificationOverviewComponent, {
            remove: { imports: [CourseNotificationComponent, CourseNotificationBubbleComponent, TranslateDirective, CourseNotificationPresetPickerComponent] },
            add: {
                imports: [
                    MockComponent(CourseNotificationComponent),
                    MockComponent(CourseNotificationBubbleComponent),
                    MockDirective(TranslateDirective),
                    MockComponent(CourseNotificationPresetPickerComponent),
                ],
            },
        });

        fixture = TestBed.createComponent(CourseNotificationOverviewComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('courseId', 101);

        fixture.detectChanges();
        componentAsAny = component as any;
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should initialize with the correct default values', () => {
        expect(componentAsAny.isShown()).toBe(false);
        expect(componentAsAny.selectedCategory).toBe(CourseNotificationCategory.GENERAL);
        expect(componentAsAny.notifications).toBeUndefined();
        expect(componentAsAny.notificationsForSelectedCategory()).toEqual([]);
        expect(componentAsAny.courseNotificationCount()).toBe(0);
        expect(componentAsAny.pagesFinished).toBe(false);
        expect(componentAsAny.isLoading()).toBe(false);
    });

    it('should set up notification count subscription on init', () => {
        expect(courseNotificationService.getNotificationCountForCourse$).toHaveBeenCalledWith(101);

        notificationCountSubject.next(5);
        expect(componentAsAny.courseNotificationCount()).toBe(5);
    });

    it('should set up notifications subscription on init', () => {
        expect(courseNotificationService.getNotificationsForCourse$).toHaveBeenCalledWith(101);
    });

    it('should filter notifications by selected category', () => {
        const communicationNotification = createMockNotification(1, 101, CourseNotificationCategory.COMMUNICATION);
        const generalNotification = createMockNotification(2, 101, CourseNotificationCategory.GENERAL);
        componentAsAny.notifications = [communicationNotification, generalNotification];

        componentAsAny.filterNotificationsIntoCurrentCategory();

        expect(componentAsAny.notificationsForSelectedCategory()).toHaveLength(1);
        expect(componentAsAny.notificationsForSelectedCategory()[0]).toBe(generalNotification);
    });

    it('should toggle overlay visibility when toggleOverlay is called', () => {
        componentAsAny.isShown.set(false);
        const updateSpy = vi.spyOn(component as any, 'updateCurrentCategoryNotificationsToSeenOnClient');

        componentAsAny.toggleOverlay();

        expect(componentAsAny.isShown()).toBe(true);
        expect(updateSpy).not.toHaveBeenCalled();

        componentAsAny.toggleOverlay();

        expect(componentAsAny.isShown()).toBe(false);
        expect(updateSpy).toHaveBeenCalledOnce();
    });

    it('should query for more notifications if less than pageSize are available', () => {
        componentAsAny.notificationsForSelectedCategory.set(
            Array(5)
                .fill(null)
                .map((_, i) => createMockNotification(i, 101, CourseNotificationCategory.COMMUNICATION)),
        );
        componentAsAny.pagesFinished = false;
        const querySpy = vi.spyOn(component as any, 'queryCurrentCategory');

        componentAsAny.toggleOverlay();

        expect(componentAsAny.queryStartSize).toBe(5);
        expect(querySpy).toHaveBeenCalledOnce();
    });

    it('should correctly check if a category is selected', () => {
        componentAsAny.selectedCategory = CourseNotificationCategory.COMMUNICATION;

        expect(componentAsAny.isCategorySelected('COMMUNICATION')).toBe(true);
        expect(componentAsAny.isCategorySelected('GENERAL')).toBe(false);
    });

    it('should change selected category and update notifications', () => {
        const updateClientSpy = vi.spyOn(component as any, 'updateCurrentCategoryNotificationsToSeenOnClient');
        const updateServerSpy = vi.spyOn(component as any, 'updateCurrentCategoryNotificationsToSeenOnServer');
        const filterSpy = vi.spyOn(component as any, 'filterNotificationsIntoCurrentCategory');

        componentAsAny.selectCategory('GENERAL');

        expect(componentAsAny.selectedCategory).toBe(CourseNotificationCategory.GENERAL);
        expect(updateClientSpy).toHaveBeenCalledOnce();
        expect(filterSpy).toHaveBeenCalledOnce();
        expect(updateServerSpy).toHaveBeenCalledOnce();
    });

    it('should query for more notifications when changing to a category with fewer than pageSize notifications', () => {
        componentAsAny.notificationsForSelectedCategory.set(
            Array(5)
                .fill(null)
                .map((_, i) => createMockNotification(i, 101, CourseNotificationCategory.COMMUNICATION)),
        );
        componentAsAny.pagesFinished = false;
        const querySpy = vi.spyOn(component as any, 'queryCurrentCategory');

        componentAsAny.selectCategory('GENERAL');

        expect(querySpy).toHaveBeenCalledOnce();
    });

    it('should hide overlay when clicking outside the component', () => {
        componentAsAny.isShown.set(true);
        const updateSpy = vi.spyOn(component as any, 'updateCurrentCategoryNotificationsToSeenOnClient');
        const elementContainsSpy = vi.spyOn(componentAsAny.elementRef.nativeElement, 'contains').mockReturnValue(false);

        componentAsAny.onClickOutside({});

        expect(componentAsAny.isShown()).toBe(false);
        expect(updateSpy).toHaveBeenCalledOnce();
        expect(elementContainsSpy).toHaveBeenCalledOnce();
    });

    it('should not hide overlay when clicking inside the component', () => {
        componentAsAny.isShown.set(true);
        const updateSpy = vi.spyOn(component as any, 'updateCurrentCategoryNotificationsToSeenOnClient');
        const elementContainsSpy = vi.spyOn(componentAsAny.elementRef.nativeElement, 'contains').mockReturnValue(true);

        componentAsAny.onClickOutside({});

        expect(componentAsAny.isShown()).toBe(true);
        expect(updateSpy).not.toHaveBeenCalled();
        expect(elementContainsSpy).toHaveBeenCalledOnce();
    });

    it('should load more notifications when scrolling to bottom', () => {
        componentAsAny.pagesFinished = false;
        componentAsAny.isLoading.set(false);
        const querySpy = vi.spyOn(component as any, 'queryCurrentCategory');

        componentAsAny.onScrollReachBottom();

        expect(componentAsAny.isLoading()).toBe(true);
        expect(querySpy).toHaveBeenCalledOnce();
    });

    it('should not load more notifications when already loading or finished', () => {
        componentAsAny.pagesFinished = false;
        componentAsAny.isLoading.set(true);
        const querySpy = vi.spyOn(component as any, 'queryCurrentCategory');

        componentAsAny.onScrollReachBottom();

        expect(querySpy).not.toHaveBeenCalled();

        componentAsAny.pagesFinished = true;
        componentAsAny.isLoading.set(false);

        componentAsAny.onScrollReachBottom();

        expect(querySpy).not.toHaveBeenCalled();
    });

    it('should mark all shown notifications as read on client and server', () => {
        const updateClientSpy = vi.spyOn(component as any, 'updateCurrentCategoryNotificationsToSeenOnClient');
        const updateServerSpy = vi.spyOn(component as any, 'updateCurrentCategoryNotificationsToSeenOnServer');

        componentAsAny.markAllAsReadClicked();

        expect(updateClientSpy).toHaveBeenCalledOnce();
        expect(updateServerSpy).toHaveBeenCalledOnce();
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

        componentAsAny.notificationsForSelectedCategory.set([unseenNotification1, unseenNotification2, seenNotification]);

        componentAsAny.updateCurrentCategoryNotificationsToSeenOnClient();

        expect(courseNotificationService.setNotificationStatusInMap).toHaveBeenCalledWith(101, [1, 2], CourseNotificationViewingStatus.SEEN);
        expect(courseNotificationService.decreaseNotificationCountBy).toHaveBeenCalledWith(101, 2);
    });

    it('should update unseen notifications to seen on server side', () => {
        const unseenNotification1 = createMockNotification(1, 101, CourseNotificationCategory.COMMUNICATION);
        const unseenNotification2 = createMockNotification(2, 101, CourseNotificationCategory.COMMUNICATION);
        const seenNotification = createMockNotification(3, 101, CourseNotificationCategory.COMMUNICATION, CourseNotificationViewingStatus.SEEN);

        componentAsAny.notificationsForSelectedCategory.set([unseenNotification1, unseenNotification2, seenNotification]);

        componentAsAny.updateCurrentCategoryNotificationsToSeenOnServer();

        expect(courseNotificationService.setNotificationStatus).toHaveBeenCalledWith(101, [1, 2], CourseNotificationViewingStatus.SEEN);
    });

    it('should correctly identify visible unseen notification IDs', () => {
        const unseenNotification1 = createMockNotification(1, 101, CourseNotificationCategory.COMMUNICATION);
        const unseenNotification2 = createMockNotification(2, 101, CourseNotificationCategory.COMMUNICATION);
        const seenNotification = createMockNotification(3, 101, CourseNotificationCategory.COMMUNICATION, CourseNotificationViewingStatus.SEEN);

        componentAsAny.notificationsForSelectedCategory.set([unseenNotification1, unseenNotification2, seenNotification]);

        const result = componentAsAny.getVisibleUnseenNotificationIds();

        expect(result).toEqual([1, 2]);
    });

    it('should not update notifications if no unseen notifications exist', () => {
        const seenNotification = createMockNotification(3, 101, CourseNotificationCategory.COMMUNICATION, CourseNotificationViewingStatus.SEEN);
        componentAsAny.notificationsForSelectedCategory.set([seenNotification]);

        componentAsAny.updateCurrentCategoryNotificationsToSeenOnClient();
        componentAsAny.updateCurrentCategoryNotificationsToSeenOnServer();

        expect(courseNotificationService.setNotificationStatusInMap).not.toHaveBeenCalled();
        expect(courseNotificationService.decreaseNotificationCountBy).not.toHaveBeenCalled();
        expect(courseNotificationService.setNotificationStatus).not.toHaveBeenCalled();
    });

    it('should query for more notifications from service', () => {
        componentAsAny.pagesFinished = false;

        componentAsAny.queryCurrentCategory();

        expect(componentAsAny.isLoading()).toBe(true);
        expect(courseNotificationService.getNextNotificationPage).toHaveBeenCalledWith(101);
    });

    it('should set pagesFinished when service returns false', () => {
        componentAsAny.pagesFinished = false;
        vi.spyOn(courseNotificationService, 'getNextNotificationPage').mockReturnValue(false);

        componentAsAny.queryCurrentCategory();

        expect(componentAsAny.pagesFinished).toBe(true);
        expect(componentAsAny.isLoading()).toBe(false);
    });

    it('should properly clean up subscriptions on destroy', () => {
        const countUnsubscribeSpy = vi.fn();
        const notificationsUnsubscribeSpy = vi.fn();

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
        const toggleSpy = vi.spyOn(component as any, 'toggleOverlay');
        const button = fixture.debugElement.query(By.css('button'));

        button.nativeElement.click();
        fixture.changeDetectorRef.detectChanges();

        expect(toggleSpy).toHaveBeenCalledOnce();
    });

    it('should apply is-shown class when overlay is shown', () => {
        componentAsAny.isShown.set(true);

        fixture.changeDetectorRef.detectChanges();

        const overlay = fixture.debugElement.query(By.css('.course-notification-overview-overlay'));
        expect(overlay.classes['is-shown']).toBe(true);
    });

    it('should display loading indicator when isLoading is true', () => {
        componentAsAny.isLoading.set(true);

        fixture.changeDetectorRef.detectChanges();

        const loadingIndicator = fixture.debugElement.query(By.css('.course-notification-overview-notification-loading'));
        expect(loadingIndicator).not.toBeNull();
    });

    it('should display empty state when no notifications are available', () => {
        componentAsAny.isLoading.set(false);
        componentAsAny.notificationsForSelectedCategory.set([]);

        fixture.changeDetectorRef.detectChanges();

        const emptyState = fixture.debugElement.query(By.css('.course-notification-overview-notification-empty-prompt'));
        expect(emptyState).not.toBeNull();
    });

    it('should display notifications when available', () => {
        componentAsAny.isLoading.set(false);
        componentAsAny.notificationsForSelectedCategory.set([createMockNotification(1, 101, CourseNotificationCategory.COMMUNICATION)]);

        fixture.changeDetectorRef.detectChanges();

        const notificationElements = fixture.debugElement.queryAll(By.css('jhi-course-notification'));
        expect(notificationElements).toHaveLength(1);
    });

    it('should display correct categories and handle category selection', () => {
        fixture.changeDetectorRef.detectChanges();

        const categoryElements = fixture.debugElement.queryAll(By.css('.course-notification-overview-category'));
        expect(categoryElements.length).toBeGreaterThan(0);

        const selectCategorySpy = vi.spyOn(component as any, 'selectCategory');

        categoryElements[0].nativeElement.click();

        expect(selectCategorySpy).toHaveBeenCalled();
    });

    describe('notification setting presets', () => {
        it('should fetch the setting info and notification info on init', () => {
            expect(courseNotificationSettingService.getSettingInfo).toHaveBeenCalledWith(101, false);
            expect(courseNotificationService.getInfo).toHaveBeenCalledOnce();
        });

        it('should initialize the selectable and selected presets once both responses are available', () => {
            expect(componentAsAny.selectableSettingPresets()).toEqual(mockNotificationSettingPresets);
            expect(componentAsAny.selectedSettingPreset()).toEqual(mockNotificationSettingPresets[0]);
        });

        it('should leave the selected preset undefined when no preset is selected', () => {
            componentAsAny.info = mockNotificationInfo;
            componentAsAny.settingInfo = { ...mockSettingInfo, selectedPreset: 0 };

            componentAsAny.initializeCourseNotificationValues();

            expect(componentAsAny.selectableSettingPresets()).toEqual(mockNotificationSettingPresets);
            expect(componentAsAny.selectedSettingPreset()).toBeUndefined();
        });

        it('should select a new preset when presetSelected is called', () => {
            componentAsAny.presetSelected(2);

            expect(courseNotificationSettingService.setSettingPreset).toHaveBeenCalledWith(101, 2, mockNotificationSettingPresets[0]);
            expect(componentAsAny.selectedSettingPreset()).toEqual(mockNotificationSettingPresets[1]);
        });

        it('should set the selected preset to undefined when custom settings are selected', () => {
            componentAsAny.presetSelected(0);

            expect(courseNotificationSettingService.setSettingPreset).toHaveBeenCalledWith(101, 0, mockNotificationSettingPresets[0]);
            expect(componentAsAny.selectedSettingPreset()).toBeUndefined();
        });

        it('should render the preset picker once presets are available', () => {
            fixture.changeDetectorRef.detectChanges();

            const presetPicker = fixture.debugElement.query(By.css('jhi-course-notification-preset-picker'));
            expect(presetPicker).not.toBeNull();
        });
    });
});
