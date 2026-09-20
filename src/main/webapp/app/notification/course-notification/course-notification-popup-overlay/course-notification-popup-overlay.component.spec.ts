import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseNotificationPopupOverlayComponent } from 'app/notification/course-notification/course-notification-popup-overlay/course-notification-popup-overlay.component';
import { CourseNotificationWebsocketService } from 'app/notification/course-notification/course-notification-websocket.service';
import { CourseNotificationService } from 'app/notification/course-notification/course-notification.service';
import { CourseNotification } from 'app/notification/shared/entities/course-notification/course-notification';
import { CourseNotificationCategory } from 'app/notification/shared/entities/course-notification/course-notification-category';
import { CourseNotificationViewingStatus } from 'app/notification/shared/entities/course-notification/course-notification-viewing-status';
import { Subject } from 'rxjs';
import dayjs from 'dayjs/esm';
import { By } from '@angular/platform-browser';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, NavigationEnd, ParamMap, Router, convertToParamMap } from '@angular/router';
import { CourseNotificationComponent } from 'app/notification/course-notification/course-notification/course-notification.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faTimes } from '@fortawesome/free-solid-svg-icons';
import { MockComponent } from 'ng-mocks';
import { of } from 'rxjs';
import { ConversationSelectionState } from 'app/communication/shared/course-conversations/course-conversation-selection.state';

describe('CourseNotificationPopupOverlayComponent', () => {
    let component: CourseNotificationPopupOverlayComponent;
    let fixture: ComponentFixture<CourseNotificationPopupOverlayComponent>;
    let courseNotificationWebsocketService: CourseNotificationWebsocketService;
    let courseNotificationService: CourseNotificationService;
    let conversationSelectionState: ConversationSelectionState;
    let websocketNotificationSubject: Subject<CourseNotification>;
    let mockRoute: any;
    let componentAsAny: any;
    let routerEvents: Subject<NavigationEnd>;
    type RouteNode = { outlet?: string; paramMap: ParamMap; children: RouteNode[] };
    let mockRouter: { events: Subject<NavigationEnd>; routerState: { snapshot: { root: RouteNode } } };

    function navigate(courseId?: number) {
        const params = courseId === undefined ? {} : { courseId: String(courseId) };
        mockRoute.firstChild.firstChild.snapshot.paramMap = convertToParamMap(params);
        // A deeper primary route exercises management routes, not just the old fixed-depth student route.
        mockRouter.routerState.snapshot.root = {
            paramMap: convertToParamMap({}),
            children: [{ outlet: 'primary', paramMap: convertToParamMap({}), children: [{ outlet: 'primary', paramMap: convertToParamMap(params), children: [] }] }],
        };
        routerEvents.next(new NavigationEnd(1, courseId ? `/courses/${courseId}` : '/', courseId ? `/courses/${courseId}` : '/'));
        fixture?.changeDetectorRef.detectChanges();
    }

    const createMockNotification = (id: number, courseId: number, channelId: number | undefined): CourseNotification => {
        return new CourseNotification(
            id,
            courseId,
            'newPostNotification',
            CourseNotificationCategory.COMMUNICATION,
            CourseNotificationViewingStatus.UNSEEN,
            dayjs(),
            'Test Course',
            'test-icon-url',
            { channelId: channelId },
            '/',
        );
    };

    const createMockAnswerNotification = (id: number, courseId: number, channelId: number, postId: number): CourseNotification => {
        return new CourseNotification(
            id,
            courseId,
            'newAnswerNotification',
            CourseNotificationCategory.COMMUNICATION,
            CourseNotificationViewingStatus.UNSEEN,
            dayjs(),
            'Test Course',
            'test-icon-url',
            { channelId: channelId, postId: postId },
            '/',
        );
    };

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    beforeEach(async () => {
        vi.useFakeTimers();
        websocketNotificationSubject = new Subject<CourseNotification>();

        conversationSelectionState = new ConversationSelectionState();

        courseNotificationWebsocketService = {
            websocketNotification$: websocketNotificationSubject.asObservable(),
        } as unknown as CourseNotificationWebsocketService;

        courseNotificationService = {
            setNotificationStatus: vi.fn(),
            setNotificationStatusInMap: vi.fn(),
            decreaseNotificationCountBy: vi.fn(),
            getIconFromType: vi.fn().mockReturnValue(faTimes),
            getDateTranslationKey: vi.fn().mockReturnValue('artemisApp.courseNotification.temporal.now'),
            getDateTranslationParams: vi.fn().mockReturnValue({}),
        } as unknown as CourseNotificationService;

        mockRoute = {
            data: of({ course: { id: 1 } }),
            firstChild: {
                firstChild: {
                    snapshot: {
                        paramMap: convertToParamMap({
                            courseId: '101',
                        }),
                    },
                },
            },
            snapshot: {
                queryParamMap: convertToParamMap({
                    conversationId: '20',
                }),
            },
        };
        routerEvents = new Subject<NavigationEnd>();
        mockRouter = {
            events: routerEvents,
            routerState: {
                snapshot: {
                    root: {
                        paramMap: convertToParamMap({}),
                        children: [{ outlet: 'primary', paramMap: convertToParamMap({ courseId: '101' }), children: [] }],
                    },
                },
            },
        };

        await TestBed.configureTestingModule({
            imports: [CommonModule, FaIconComponent, CourseNotificationPopupOverlayComponent],
            providers: [
                { provide: CourseNotificationWebsocketService, useValue: courseNotificationWebsocketService },
                { provide: CourseNotificationService, useValue: courseNotificationService },
                { provide: ActivatedRoute, useValue: mockRoute },
                { provide: Router, useValue: mockRouter },
                { provide: ConversationSelectionState, useValue: conversationSelectionState },
            ],
        }).overrideComponent(CourseNotificationPopupOverlayComponent, {
            remove: { imports: [CourseNotificationComponent] },
            add: { imports: [MockComponent(CourseNotificationComponent)] },
        });

        fixture = TestBed.createComponent(CourseNotificationPopupOverlayComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        componentAsAny = component as any;
    });

    it('renders only the active course without marking other courses seen', () => {
        navigate(102);
        websocketNotificationSubject.next(createMockNotification(1, 101, 0));
        websocketNotificationSubject.next(createMockNotification(2, 102, 0));
        fixture.changeDetectorRef.detectChanges();
        const rendered = fixture.debugElement.queryAll(By.directive(CourseNotificationComponent));
        expect(rendered.map((element) => element.componentInstance.courseNotification().courseId)).toEqual([102]);
        expect(courseNotificationService.setNotificationStatus).not.toHaveBeenCalled();
        expect(courseNotificationService.setNotificationStatusInMap).not.toHaveBeenCalled();
        expect(courseNotificationService.decreaseNotificationCountBy).not.toHaveBeenCalled();
    });

    it('prunes queued popups on course navigation without losing unread history', () => {
        websocketNotificationSubject.next(createMockNotification(1, 101, 0));
        fixture.changeDetectorRef.detectChanges();
        expect(fixture.debugElement.queryAll(By.directive(CourseNotificationComponent))).toHaveLength(1);
        componentAsAny.isExpanded.set(true);
        navigate(102);
        expect(fixture.debugElement.queryAll(By.directive(CourseNotificationComponent))).toHaveLength(0);
        expect(componentAsAny.isExpanded()).toBe(false);
        navigate(101);
        expect(fixture.debugElement.queryAll(By.directive(CourseNotificationComponent))).toHaveLength(0);
        expect(courseNotificationService.setNotificationStatus).not.toHaveBeenCalled();
        expect(courseNotificationService.setNotificationStatusInMap).not.toHaveBeenCalled();
        expect(courseNotificationService.decreaseNotificationCountBy).not.toHaveBeenCalled();
    });

    it('retains global popup delivery on non-course pages', () => {
        navigate();
        websocketNotificationSubject.next(createMockNotification(1, 101, 0));
        websocketNotificationSubject.next(createMockNotification(2, 102, 0));
        fixture.changeDetectorRef.detectChanges();
        expect(fixture.debugElement.queryAll(By.directive(CourseNotificationComponent))).toHaveLength(2);
    });

    it('should add notification when websocket emits one', () => {
        const mockNotification = createMockNotification(1, 101, 0);

        websocketNotificationSubject.next(mockNotification);
        fixture.changeDetectorRef.detectChanges();

        expect(componentAsAny.notifications()).toHaveLength(1);
        expect(componentAsAny.notifications()[0]).toBe(mockNotification);

        vi.clearAllTimers();
    });

    it('should not add notification when conversation is open', () => {
        const mockNotification = createMockNotification(1, 101, 20);

        websocketNotificationSubject.next(mockNotification);
        fixture.changeDetectorRef.detectChanges();

        expect(componentAsAny.notifications()).toHaveLength(0);

        vi.clearAllTimers();
    });

    it('should add reply notification when thread is not open', () => {
        const mockNotification = createMockAnswerNotification(1, 101, 20, 50);

        websocketNotificationSubject.next(mockNotification);
        fixture.changeDetectorRef.detectChanges();

        expect(componentAsAny.notifications()).toHaveLength(1);

        vi.clearAllTimers();
    });

    it('should not add reply notification when thread is open', () => {
        const mockNotification = createMockAnswerNotification(1, 101, 20, 50);

        conversationSelectionState.setOpenPostId(50);

        websocketNotificationSubject.next(mockNotification);
        fixture.changeDetectorRef.detectChanges();

        expect(componentAsAny.notifications()).toHaveLength(0);

        vi.clearAllTimers();
    });

    it('should set isExpanded to false when removing the last notification', () => {
        const mockNotification = createMockNotification(1, 101, 0);
        componentAsAny.notifications.set([mockNotification]);
        componentAsAny.isExpanded.set(true);
        fixture.changeDetectorRef.detectChanges();

        component.removeNotification(1);

        expect(componentAsAny.isExpanded()).toBe(false);
    });

    it('should handle closeClicked correctly', () => {
        const mockNotification = createMockNotification(1, 101, 0);
        componentAsAny.notifications.set([mockNotification]);
        fixture.changeDetectorRef.detectChanges();

        component.closeClicked(mockNotification);

        expect(courseNotificationService.setNotificationStatus).toHaveBeenCalledOnce();
        expect(courseNotificationService.setNotificationStatus).toHaveBeenCalledWith(101, [1], CourseNotificationViewingStatus.SEEN);

        expect(courseNotificationService.setNotificationStatusInMap).toHaveBeenCalledOnce();
        expect(courseNotificationService.setNotificationStatusInMap).toHaveBeenCalledWith(101, [1], CourseNotificationViewingStatus.SEEN);

        expect(courseNotificationService.decreaseNotificationCountBy).toHaveBeenCalledOnce();
        expect(courseNotificationService.decreaseNotificationCountBy).toHaveBeenCalledWith(101, 1);

        expect(componentAsAny.notifications()).toHaveLength(0);
    });

    it('should set isExpanded to true when overlayClicked and notifications.length > 1', () => {
        const mockNotification1 = createMockNotification(1, 101, 0);
        const mockNotification2 = createMockNotification(2, 102, 0);
        componentAsAny.notifications.set([mockNotification1, mockNotification2]);
        componentAsAny.isExpanded.set(false);
        fixture.changeDetectorRef.detectChanges();

        component.overlayClicked();

        expect(componentAsAny.isExpanded()).toBe(true);
    });

    it('should not change isExpanded when overlayClicked and notifications.length <= 1', () => {
        const mockNotification = createMockNotification(1, 101, 0);
        componentAsAny.notifications.set([mockNotification]);
        componentAsAny.isExpanded.set(false);
        fixture.changeDetectorRef.detectChanges();

        component.overlayClicked();

        expect(componentAsAny.isExpanded()).toBe(false);
    });

    it('should set isExpanded to false when collapseOverlayClicked', () => {
        componentAsAny.isExpanded.set(true);
        fixture.changeDetectorRef.detectChanges();

        component.collapseOverlayClicked();
        vi.advanceTimersByTime(0);

        expect(componentAsAny.isExpanded()).toBe(false);
    });

    it('should do nothing when collapseOverlayClicked and isExpanded is false', () => {
        componentAsAny.isExpanded.set(false);
        fixture.changeDetectorRef.detectChanges();

        component.collapseOverlayClicked();
        vi.advanceTimersByTime(0);

        expect(componentAsAny.isExpanded()).toBe(false);
    });

    it('stops receiving popups after destruction', () => {
        fixture.destroy();
        websocketNotificationSubject.next(createMockNotification(1, 101, 0));
        expect(componentAsAny.notifications()).toEqual([]);
        expect(courseNotificationService.setNotificationStatus).not.toHaveBeenCalled();
    });

    it('should display notifications in the template', () => {
        const mockNotification = createMockNotification(1, 101, 0);
        componentAsAny.notifications.set([mockNotification]);

        fixture.changeDetectorRef.detectChanges();

        const notificationElements = fixture.debugElement.queryAll(By.css('.course-notification-popup-overlay-notification'));
        expect(notificationElements).toHaveLength(1);
    });

    it('should add d-none class when no notifications are present', () => {
        componentAsAny.notifications.set([]);

        fixture.changeDetectorRef.detectChanges();

        const overlayElement = fixture.debugElement.query(By.css('.course-notification-popup-overlay'));
        expect(overlayElement.nativeElement.classList).toContain('hidden');
    });

    it('should add is-expanded class when isExpanded is true', () => {
        const mockNotification = createMockNotification(1, 101, 0);
        componentAsAny.notifications.set([mockNotification]);
        componentAsAny.isExpanded.set(true);

        fixture.changeDetectorRef.detectChanges();

        const overlayElement = fixture.debugElement.query(By.css('.course-notification-popup-overlay'));
        expect(overlayElement.nativeElement.classList).toContain('is-expanded');
    });

    it('should clear all notifications when clearAllNotifications is called', () => {
        const mockNotification1 = createMockNotification(1, 101, 0);
        const mockNotification2 = createMockNotification(2, 102, 0);
        componentAsAny.notifications.set([mockNotification1, mockNotification2]);
        componentAsAny.isExpanded.set(true);
        fixture.changeDetectorRef.detectChanges();

        component.clearAllNotifications();
        vi.advanceTimersByTime(0); // Process the setTimeout

        expect(componentAsAny.notifications()).toHaveLength(0);
        expect(componentAsAny.isExpanded()).toBe(false);
    });

    it('should do nothing when clearAllNotifications is called and isExpanded is false', () => {
        const mockNotification = createMockNotification(1, 101, 0);
        componentAsAny.notifications.set([mockNotification]);
        componentAsAny.isExpanded.set(false);
        fixture.changeDetectorRef.detectChanges();

        const setNotificationStatusSpy = vi.spyOn(courseNotificationService, 'setNotificationStatus');
        const setNotificationStatusInMapSpy = vi.spyOn(courseNotificationService, 'setNotificationStatusInMap');
        const decreaseNotificationCountBySpy = vi.spyOn(courseNotificationService, 'decreaseNotificationCountBy');

        component.clearAllNotifications();

        expect(setNotificationStatusSpy).not.toHaveBeenCalled();
        expect(setNotificationStatusInMapSpy).not.toHaveBeenCalled();
        expect(decreaseNotificationCountBySpy).not.toHaveBeenCalled();
        expect(componentAsAny.notifications()).toHaveLength(1);
    });

    it('should mark all notifications as seen on server when clearAllNotifications is called', () => {
        const mockNotification1 = createMockNotification(1, 101, 0);
        const mockNotification2 = createMockNotification(2, 101, 0);
        const mockNotification3 = createMockNotification(3, 102, 0);
        componentAsAny.notifications.set([mockNotification1, mockNotification2, mockNotification3]);
        componentAsAny.isExpanded.set(true);
        fixture.changeDetectorRef.detectChanges();

        component.clearAllNotifications();
        vi.advanceTimersByTime(0);

        expect(courseNotificationService.setNotificationStatus).toHaveBeenCalledWith(101, [1, 2], CourseNotificationViewingStatus.SEEN);
        expect(courseNotificationService.setNotificationStatusInMap).toHaveBeenCalledWith(101, [1, 2], CourseNotificationViewingStatus.SEEN);
        expect(courseNotificationService.decreaseNotificationCountBy).toHaveBeenCalledWith(101, 2);

        expect(courseNotificationService.setNotificationStatus).toHaveBeenCalledWith(102, [3], CourseNotificationViewingStatus.SEEN);
        expect(courseNotificationService.setNotificationStatusInMap).toHaveBeenCalledWith(102, [3], CourseNotificationViewingStatus.SEEN);
        expect(courseNotificationService.decreaseNotificationCountBy).toHaveBeenCalledWith(102, 1);
    });
});
