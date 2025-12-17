import { ComponentFixture, TestBed, discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { CourseNotificationPopupOverlayComponent } from 'app/communication/course-notification/course-notification-popup-overlay/course-notification-popup-overlay.component';
import { CourseNotificationWebsocketService } from 'app/communication/course-notification/course-notification-websocket.service';
import { CourseNotificationService } from 'app/communication/course-notification/course-notification.service';
import { CourseNotification } from 'app/communication/shared/entities/course-notification/course-notification';
import { CourseNotificationCategory } from 'app/communication/shared/entities/course-notification/course-notification-category';
import { CourseNotificationViewingStatus } from 'app/communication/shared/entities/course-notification/course-notification-viewing-status';
import { Subject } from 'rxjs';
import dayjs from 'dayjs/esm';
import { By } from '@angular/platform-browser';
import { CommonModule } from '@angular/common';
import { CourseNotificationComponent } from 'app/communication/course-notification/course-notification/course-notification.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockComponent } from 'ng-mocks';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

describe('CourseNotificationPopupOverlayComponent', () => {
    let component: CourseNotificationPopupOverlayComponent;
    let fixture: ComponentFixture<CourseNotificationPopupOverlayComponent>;
    let courseNotificationWebsocketService: CourseNotificationWebsocketService;
    let courseNotificationService: CourseNotificationService;
    let websocketNotificationSubject: Subject<CourseNotification>;
    let componentAsAny: any;

    const createMockNotification = (id: number, courseId: number): CourseNotification => {
        return new CourseNotification(
            id,
            courseId,
            'newPostNotification',
            CourseNotificationCategory.COMMUNICATION,
            CourseNotificationViewingStatus.UNSEEN,
            dayjs(),
            {
                courseTitle: 'Test Course',
                courseIconUrl: 'test-icon-url',
            },
            '/',
        );
    };

    beforeEach(async () => {
        websocketNotificationSubject = new Subject<CourseNotification>();

        courseNotificationWebsocketService = {
            websocketNotification$: websocketNotificationSubject.asObservable(),
        } as unknown as CourseNotificationWebsocketService;

        courseNotificationService = {
            setNotificationStatus: jest.fn(),
            setNotificationStatusInMap: jest.fn(),
            decreaseNotificationCountBy: jest.fn(),
        } as unknown as CourseNotificationService;

        await TestBed.configureTestingModule({
            imports: [BrowserAnimationsModule, CommonModule, NoopAnimationsModule, FaIconComponent],
            declarations: [CourseNotificationPopupOverlayComponent, MockComponent(CourseNotificationComponent)],
            providers: [
                { provide: CourseNotificationWebsocketService, useValue: courseNotificationWebsocketService },
                { provide: CourseNotificationService, useValue: courseNotificationService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseNotificationPopupOverlayComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        componentAsAny = component as any;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should add notification when websocket emits one', fakeAsync(() => {
        const mockNotification = createMockNotification(1, 101);

        websocketNotificationSubject.next(mockNotification);
        fixture.changeDetectorRef.detectChanges();

        expect(componentAsAny.notifications).toHaveLength(1);
        expect(componentAsAny.notifications[0]).toBe(mockNotification);

        discardPeriodicTasks();
    }));

    it('should set isExpanded to false when removing the last notification', () => {
        const mockNotification = createMockNotification(1, 101);
        componentAsAny.notifications = [mockNotification];
        componentAsAny.isExpanded = true;
        fixture.changeDetectorRef.detectChanges();

        component.removeNotification(1);

        expect(componentAsAny.isExpanded).toBeFalse();
    });

    it('should handle closeClicked correctly', () => {
        const mockNotification = createMockNotification(1, 101);
        componentAsAny.notifications = [mockNotification];
        fixture.changeDetectorRef.detectChanges();

        component.closeClicked(mockNotification);

        expect(courseNotificationService.setNotificationStatus).toHaveBeenCalledOnce();
        expect(courseNotificationService.setNotificationStatus).toHaveBeenCalledWith(101, [1], CourseNotificationViewingStatus.SEEN);

        expect(courseNotificationService.setNotificationStatusInMap).toHaveBeenCalledOnce();
        expect(courseNotificationService.setNotificationStatusInMap).toHaveBeenCalledWith(101, [1], CourseNotificationViewingStatus.SEEN);

        expect(courseNotificationService.decreaseNotificationCountBy).toHaveBeenCalledOnce();
        expect(courseNotificationService.decreaseNotificationCountBy).toHaveBeenCalledWith(101, 1);

        expect(componentAsAny.notifications).toHaveLength(0);
    });

    it('should set isExpanded to true when overlayClicked and notifications.length > 1', () => {
        const mockNotification1 = createMockNotification(1, 101);
        const mockNotification2 = createMockNotification(2, 102);
        componentAsAny.notifications = [mockNotification1, mockNotification2];
        componentAsAny.isExpanded = false;
        fixture.changeDetectorRef.detectChanges();

        component.overlayClicked();

        expect(componentAsAny.isExpanded).toBeTrue();
    });

    it('should not change isExpanded when overlayClicked and notifications.length <= 1', () => {
        const mockNotification = createMockNotification(1, 101);
        componentAsAny.notifications = [mockNotification];
        componentAsAny.isExpanded = false;
        fixture.changeDetectorRef.detectChanges();

        component.overlayClicked();

        expect(componentAsAny.isExpanded).toBeFalse();
    });

    it('should set isExpanded to false when collapseOverlayClicked', fakeAsync(() => {
        componentAsAny.isExpanded = true;
        fixture.changeDetectorRef.detectChanges();

        component.collapseOverlayClicked();
        tick(0);

        expect(componentAsAny.isExpanded).toBeFalse();
    }));

    it('should do nothing when collapseOverlayClicked and isExpanded is false', fakeAsync(() => {
        componentAsAny.isExpanded = false;
        fixture.changeDetectorRef.detectChanges();

        component.collapseOverlayClicked();
        tick(0);

        expect(componentAsAny.isExpanded).toBeFalse();
    }));

    it('should unsubscribe from websocket on ngOnDestroy', () => {
        const unsubscribeSpy = jest.spyOn(componentAsAny.courseNotificationWebsocketSubscription, 'unsubscribe');

        component.ngOnDestroy();

        expect(unsubscribeSpy).toHaveBeenCalledOnce();
    });

    it('should display notifications in the template', () => {
        const mockNotification = createMockNotification(1, 101);
        componentAsAny.notifications = [mockNotification];

        fixture.changeDetectorRef.detectChanges();

        const notificationElements = fixture.debugElement.queryAll(By.css('.course-notification-popup-overlay-notification'));
        expect(notificationElements).toHaveLength(1);
    });

    it('should add d-none class when no notifications are present', () => {
        componentAsAny.notifications = [];

        fixture.changeDetectorRef.detectChanges();

        const overlayElement = fixture.debugElement.query(By.css('.course-notification-popup-overlay'));
        expect(overlayElement.nativeElement.classList).toContain('d-none');
    });

    it('should add is-expanded class when isExpanded is true', () => {
        const mockNotification = createMockNotification(1, 101);
        componentAsAny.notifications = [mockNotification];
        componentAsAny.isExpanded = true;

        fixture.changeDetectorRef.detectChanges();

        const overlayElement = fixture.debugElement.query(By.css('.course-notification-popup-overlay'));
        expect(overlayElement.nativeElement.classList).toContain('is-expanded');
    });

    it('should trigger overlayClicked when clicking the overlay', () => {
        const mockNotification1 = createMockNotification(1, 101);
        const mockNotification2 = createMockNotification(2, 102);
        componentAsAny.notifications = [mockNotification1, mockNotification2];
        fixture.changeDetectorRef.detectChanges();
        const overlayClickedSpy = jest.spyOn(component, 'overlayClicked');
        const overlayElement = fixture.debugElement.query(By.css('.course-notification-popup-overlay'));

        overlayElement.nativeElement.click();

        expect(overlayClickedSpy).toHaveBeenCalledOnce();
    });

    it('should trigger collapseOverlayClicked when clicking the collapse button', () => {
        const mockNotification = createMockNotification(1, 101);
        componentAsAny.notifications = [mockNotification];
        fixture.changeDetectorRef.detectChanges();
        const collapseOverlayClickedSpy = jest.spyOn(component, 'collapseOverlayClicked');
        const collapseButton = fixture.debugElement.query(By.css('.btn-outline-primary'));

        collapseButton.nativeElement.click();

        expect(collapseOverlayClickedSpy).toHaveBeenCalledOnce();
    });

    it('should clear all notifications when clearAllNotifications is called', fakeAsync(() => {
        const mockNotification1 = createMockNotification(1, 101);
        const mockNotification2 = createMockNotification(2, 102);
        componentAsAny.notifications = [mockNotification1, mockNotification2];
        componentAsAny.isExpanded = true;
        fixture.changeDetectorRef.detectChanges();

        component.clearAllNotifications();
        tick(0); // Process the setTimeout

        expect(componentAsAny.notifications).toHaveLength(0);
        expect(componentAsAny.isExpanded).toBeFalse();
    }));

    it('should do nothing when clearAllNotifications is called and isExpanded is false', () => {
        const mockNotification = createMockNotification(1, 101);
        componentAsAny.notifications = [mockNotification];
        componentAsAny.isExpanded = false;
        fixture.changeDetectorRef.detectChanges();

        const setNotificationStatusSpy = jest.spyOn(courseNotificationService, 'setNotificationStatus');
        const setNotificationStatusInMapSpy = jest.spyOn(courseNotificationService, 'setNotificationStatusInMap');
        const decreaseNotificationCountBySpy = jest.spyOn(courseNotificationService, 'decreaseNotificationCountBy');

        component.clearAllNotifications();

        expect(setNotificationStatusSpy).not.toHaveBeenCalled();
        expect(setNotificationStatusInMapSpy).not.toHaveBeenCalled();
        expect(decreaseNotificationCountBySpy).not.toHaveBeenCalled();
        expect(componentAsAny.notifications).toHaveLength(1);
    });

    it('should mark all notifications as seen on server when clearAllNotifications is called', fakeAsync(() => {
        const mockNotification1 = createMockNotification(1, 101);
        const mockNotification2 = createMockNotification(2, 101);
        const mockNotification3 = createMockNotification(3, 102);
        componentAsAny.notifications = [mockNotification1, mockNotification2, mockNotification3];
        componentAsAny.isExpanded = true;
        fixture.changeDetectorRef.detectChanges();

        component.clearAllNotifications();
        tick(0);

        expect(courseNotificationService.setNotificationStatus).toHaveBeenCalledWith(101, [1, 2], CourseNotificationViewingStatus.SEEN);
        expect(courseNotificationService.setNotificationStatusInMap).toHaveBeenCalledWith(101, [1, 2], CourseNotificationViewingStatus.SEEN);
        expect(courseNotificationService.decreaseNotificationCountBy).toHaveBeenCalledWith(101, 2);

        expect(courseNotificationService.setNotificationStatus).toHaveBeenCalledWith(102, [3], CourseNotificationViewingStatus.SEEN);
        expect(courseNotificationService.setNotificationStatusInMap).toHaveBeenCalledWith(102, [3], CourseNotificationViewingStatus.SEEN);
        expect(courseNotificationService.decreaseNotificationCountBy).toHaveBeenCalledWith(102, 1);
    }));

    it('should trigger clearAllNotifications when clicking the clear button', () => {
        const mockNotification = createMockNotification(1, 101);
        componentAsAny.notifications = [mockNotification];
        componentAsAny.isExpanded = true;
        fixture.changeDetectorRef.detectChanges();

        const clearAllNotificationsSpy = jest.spyOn(component, 'clearAllNotifications');

        const clearButton = fixture.debugElement.queryAll(By.css('.btn-outline-primary'))[1];
        clearButton.nativeElement.click();

        expect(clearAllNotificationsSpy).toHaveBeenCalledOnce();
    });
});
