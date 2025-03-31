import { ComponentFixture, TestBed, fakeAsync, tick, discardPeriodicTasks } from '@angular/core/testing';
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
            imports: [BrowserAnimationsModule, CommonModule, NoopAnimationsModule],
            declarations: [CourseNotificationPopupOverlayComponent, MockComponent(CourseNotificationComponent), MockComponent(FaIconComponent)],
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
        fixture.detectChanges();

        expect(componentAsAny.notifications).toHaveLength(1);
        expect(componentAsAny.notifications[0]).toBe(mockNotification);

        discardPeriodicTasks();
    }));

    it('should set isExpanded to false when removing the last notification', () => {
        const mockNotification = createMockNotification(1, 101);
        componentAsAny.notifications = [mockNotification];
        componentAsAny.isExpanded = true;
        fixture.detectChanges();

        component.removeNotification(1);

        expect(componentAsAny.isExpanded).toBeFalse();
    });

    it('should handle closeClicked correctly', () => {
        const mockNotification = createMockNotification(1, 101);
        componentAsAny.notifications = [mockNotification];
        fixture.detectChanges();

        component.closeClicked(mockNotification);

        expect(courseNotificationService.setNotificationStatus).toHaveBeenCalledTimes(1);
        expect(courseNotificationService.setNotificationStatus).toHaveBeenCalledWith(101, [1], CourseNotificationViewingStatus.SEEN);

        expect(courseNotificationService.setNotificationStatusInMap).toHaveBeenCalledTimes(1);
        expect(courseNotificationService.setNotificationStatusInMap).toHaveBeenCalledWith(101, [1], CourseNotificationViewingStatus.SEEN);

        expect(courseNotificationService.decreaseNotificationCountBy).toHaveBeenCalledTimes(1);
        expect(courseNotificationService.decreaseNotificationCountBy).toHaveBeenCalledWith(101, 1);

        expect(componentAsAny.notifications).toHaveLength(0);
    });

    it('should set isExpanded to true when overlayClicked and notifications.length > 1', () => {
        const mockNotification1 = createMockNotification(1, 101);
        const mockNotification2 = createMockNotification(2, 102);
        componentAsAny.notifications = [mockNotification1, mockNotification2];
        componentAsAny.isExpanded = false;
        fixture.detectChanges();

        component.overlayClicked();

        expect(componentAsAny.isExpanded).toBeTrue();
    });

    it('should not change isExpanded when overlayClicked and notifications.length <= 1', () => {
        const mockNotification = createMockNotification(1, 101);
        componentAsAny.notifications = [mockNotification];
        componentAsAny.isExpanded = false;
        fixture.detectChanges();

        component.overlayClicked();

        expect(componentAsAny.isExpanded).toBeFalse();
    });

    it('should set isExpanded to false when collapseOverlayClicked', fakeAsync(() => {
        componentAsAny.isExpanded = true;
        fixture.detectChanges();

        component.collapseOverlayClicked();
        tick(0);

        expect(componentAsAny.isExpanded).toBeFalse();
    }));

    it('should do nothing when collapseOverlayClicked and isExpanded is false', fakeAsync(() => {
        componentAsAny.isExpanded = false;
        fixture.detectChanges();

        component.collapseOverlayClicked();
        tick(0);

        expect(componentAsAny.isExpanded).toBeFalse();
    }));

    it('should unsubscribe from websocket on ngOnDestroy', () => {
        const unsubscribeSpy = jest.spyOn(componentAsAny.courseNotificationWebsocketSubscription, 'unsubscribe');

        component.ngOnDestroy();

        expect(unsubscribeSpy).toHaveBeenCalledTimes(1);
    });

    it('should display notifications in the template', () => {
        const mockNotification = createMockNotification(1, 101);
        componentAsAny.notifications = [mockNotification];

        fixture.detectChanges();

        const notificationElements = fixture.debugElement.queryAll(By.css('.course-notification-popup-overlay-notification'));
        expect(notificationElements).toHaveLength(1);
    });

    it('should add d-none class when no notifications are present', () => {
        componentAsAny.notifications = [];

        fixture.detectChanges();

        const overlayElement = fixture.debugElement.query(By.css('.course-notification-popup-overlay'));
        expect(overlayElement.nativeElement.classList).toContain('d-none');
    });

    it('should add is-expanded class when isExpanded is true', () => {
        const mockNotification = createMockNotification(1, 101);
        componentAsAny.notifications = [mockNotification];
        componentAsAny.isExpanded = true;

        fixture.detectChanges();

        const overlayElement = fixture.debugElement.query(By.css('.course-notification-popup-overlay'));
        expect(overlayElement.nativeElement.classList).toContain('is-expanded');
    });

    it('should trigger overlayClicked when clicking the overlay', () => {
        const mockNotification1 = createMockNotification(1, 101);
        const mockNotification2 = createMockNotification(2, 102);
        componentAsAny.notifications = [mockNotification1, mockNotification2];
        fixture.detectChanges();
        const overlayClickedSpy = jest.spyOn(component, 'overlayClicked');
        const overlayElement = fixture.debugElement.query(By.css('.course-notification-popup-overlay'));

        overlayElement.nativeElement.click();

        expect(overlayClickedSpy).toHaveBeenCalledTimes(1);
    });

    it('should trigger collapseOverlayClicked when clicking the collapse button', () => {
        const mockNotification = createMockNotification(1, 101);
        componentAsAny.notifications = [mockNotification];
        fixture.detectChanges();
        const collapseOverlayClickedSpy = jest.spyOn(component, 'collapseOverlayClicked');
        const collapseButton = fixture.debugElement.query(By.css('.btn-outline-primary'));

        collapseButton.nativeElement.click();

        expect(collapseOverlayClickedSpy).toHaveBeenCalledTimes(1);
    });
});
