import { TestBed } from '@angular/core/testing';
import { CourseNotificationWebsocketService } from 'app/communication/course-notification/course-notification-websocket.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { CourseNotificationService } from 'app/communication/course-notification/course-notification.service';
import { BehaviorSubject, Subject } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { CourseNotification } from 'app/entities/course-notification/course-notification';
import { CourseNotificationCategory } from 'app/entities/course-notification/course-notification-category';
import { CourseNotificationViewingStatus } from 'app/entities/course-notification/course-notification-viewing-status';
import { CourseManagementService } from 'app/core/course/manage/course-management.service';

describe('CourseNotificationWebsocketService', () => {
    let service: CourseNotificationWebsocketService;
    let websocketServiceMock: jest.Mocked<WebsocketService>;
    let courseNotificationServiceMock: jest.Mocked<CourseNotificationService>;
    let courseManagementServiceMock: jest.Mocked<CourseManagementService>;
    let coursesSubject: BehaviorSubject<Course[] | undefined>;
    let websocketReceiveSubject: Subject<CourseNotification>;

    beforeEach(() => {
        websocketReceiveSubject = new Subject<CourseNotification>();
        websocketServiceMock = {
            subscribe: jest.fn().mockReturnThis(),
            receive: jest.fn().mockReturnValue(websocketReceiveSubject.asObservable()),
        } as unknown as jest.Mocked<WebsocketService>;

        courseNotificationServiceMock = {
            addNotification: jest.fn(),
        } as unknown as jest.Mocked<CourseNotificationService>;

        coursesSubject = new BehaviorSubject<Course[] | undefined>(undefined);
        courseManagementServiceMock = {
            getCoursesForNotifications: jest.fn().mockReturnValue(coursesSubject),
        } as unknown as jest.Mocked<CourseManagementService>;

        TestBed.configureTestingModule({
            providers: [
                CourseNotificationWebsocketService,
                { provide: WebsocketService, useValue: websocketServiceMock },
                { provide: CourseNotificationService, useValue: courseNotificationServiceMock },
                { provide: CourseManagementService, useValue: courseManagementServiceMock },
            ],
        });

        service = TestBed.inject(CourseNotificationWebsocketService);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    it('should subscribe to courses on initialization', () => {
        const courses = [
            { id: 1, title: 'Course 1' },
            { id: 2, title: 'Course 2' },
        ] as Course[];

        coursesSubject.next(courses);

        expect(websocketServiceMock.subscribe).toHaveBeenCalledTimes(2);
        expect(websocketServiceMock.subscribe).toHaveBeenCalledWith('/user/topic/communication/notification/1');
        expect(websocketServiceMock.subscribe).toHaveBeenCalledWith('/user/topic/communication/notification/2');
        expect(websocketServiceMock.receive).toHaveBeenCalledTimes(2);
        expect(websocketServiceMock.receive).toHaveBeenCalledWith('/user/topic/communication/notification/1');
        expect(websocketServiceMock.receive).toHaveBeenCalledWith('/user/topic/communication/notification/2');
    });

    it('should not resubscribe to same course twice', () => {
        jest.clearAllMocks();

        const courses = [{ id: 1, title: 'Course 1' }] as Course[];

        coursesSubject.next(courses);
        coursesSubject.next(courses);

        expect(websocketServiceMock.subscribe).toHaveBeenCalledTimes(1);
        expect(websocketServiceMock.receive).toHaveBeenCalledTimes(1);
    });

    it('should handle incoming notifications and pass them to the notification service', () => {
        const courses = [{ id: 1, title: 'Course 1' }] as Course[];
        coursesSubject.next(courses);

        const incomingNotification: CourseNotification = {
            notificationId: 123,
            courseId: 1,
            notificationType: 'newPostNotification',
            category: 'GENERAL' as unknown as CourseNotificationCategory,
            status: 'UNSEEN' as unknown as CourseNotificationViewingStatus,
            // @ts-ignore
            creationDate: new Date('2024-01-15T10:00:00'),
            parameters: { key: 'value' },
        };

        websocketReceiveSubject.next(incomingNotification);

        expect(courseNotificationServiceMock.addNotification).toHaveBeenCalledTimes(1);

        expect(courseNotificationServiceMock.addNotification.mock.calls[0][0]).toBe(1);

        const processedNotification = courseNotificationServiceMock.addNotification.mock.calls[0][1];
        expect(processedNotification.notificationId).toBe(123);
        expect(processedNotification.courseId).toBe(1);
        expect(processedNotification.notificationType).toBe('newPostNotification');
        // We can't directly compare the dayjs object, but we can check its functionality
        expect(processedNotification.creationDate).toBeDefined();
        expect(processedNotification.parameters).toEqual({ key: 'value' });
    });

    it('should emit received notifications via websocketNotification$ observable', (done) => {
        service.websocketNotification$.subscribe((notification) => {
            expect(notification.notificationId).toBe(123);
            expect(notification.courseId).toBe(1);
            done();
        });

        const courses = [{ id: 1, title: 'Course 1' }] as Course[];
        coursesSubject.next(courses);

        const incomingNotification: CourseNotification = {
            notificationId: 123,
            courseId: 1,
            notificationType: 'newPostNotification',
            category: 'GENERAL' as unknown as CourseNotificationCategory,
            status: 'UNSEEN' as unknown as CourseNotificationViewingStatus,
            //@ts-ignore
            creationDate: new Date('2024-01-15T10:00:00'),
            parameters: { key: 'value' },
        };

        websocketReceiveSubject.next(incomingNotification);
    });

    it('should do nothing when courses list is undefined', () => {
        coursesSubject.next(undefined);
        expect(websocketServiceMock.subscribe).not.toHaveBeenCalled();
    });

    it('should handle courses without IDs properly', () => {
        const courses = [{ title: 'Course without ID' }, { id: 2, title: 'Course 2' }] as Course[];

        coursesSubject.next(courses);

        expect(websocketServiceMock.subscribe).toHaveBeenCalledTimes(1);
        expect(websocketServiceMock.subscribe).toHaveBeenCalledWith('/user/topic/communication/notification/2');
    });
});
