import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { CourseNotificationWebsocketService } from 'app/communication/course-notification/course-notification-websocket.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { CourseNotificationService } from 'app/communication/course-notification/course-notification.service';
import { BehaviorSubject, Subject, firstValueFrom } from 'rxjs';
import { Course } from 'app/core/course/shared/entities/course.model';
import { CourseNotification } from 'app/communication/shared/entities/course-notification/course-notification';
import { CourseNotificationCategory } from 'app/communication/shared/entities/course-notification/course-notification-category';
import { CourseNotificationViewingStatus } from 'app/communication/shared/entities/course-notification/course-notification-viewing-status';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';

describe('CourseNotificationWebsocketService', () => {
    setupTestBed({ zoneless: true });

    let service: CourseNotificationWebsocketService;
    let websocketServiceMock: { subscribe: ReturnType<typeof vi.fn> };
    let courseNotificationServiceMock: { addNotification: ReturnType<typeof vi.fn> };
    let courseManagementServiceMock: { getCoursesForNotifications: ReturnType<typeof vi.fn> };
    let accountServiceMock: { getAuthenticationState: ReturnType<typeof vi.fn> };
    let coursesSubject: BehaviorSubject<Course[] | undefined>;
    let websocketReceiveSubject: Subject<CourseNotification>;
    let userSubject: BehaviorSubject<User | null>;

    afterEach(() => {
        vi.restoreAllMocks();
    });

    beforeEach(() => {
        websocketReceiveSubject = new Subject<CourseNotification>();
        websocketServiceMock = {
            subscribe: vi.fn().mockReturnValue(websocketReceiveSubject.asObservable()),
        };

        courseNotificationServiceMock = {
            addNotification: vi.fn(),
        };

        coursesSubject = new BehaviorSubject<Course[] | undefined>(undefined);
        courseManagementServiceMock = {
            getCoursesForNotifications: vi.fn().mockReturnValue(coursesSubject),
        };

        userSubject = new BehaviorSubject<User | null>(null);
        accountServiceMock = {
            getAuthenticationState: vi.fn().mockReturnValue(userSubject),
        };

        TestBed.configureTestingModule({
            providers: [
                CourseNotificationWebsocketService,
                { provide: WebsocketService, useValue: websocketServiceMock },
                { provide: CourseNotificationService, useValue: courseNotificationServiceMock },
                { provide: CourseManagementService, useValue: courseManagementServiceMock },
                { provide: AccountService, useValue: accountServiceMock },
            ],
        });

        service = TestBed.inject(CourseNotificationWebsocketService);
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    describe('Course subscriptions', () => {
        it('should subscribe to courses on initialization when user is authenticated', () => {
            const user = { id: 'user1' } as unknown as User;
            userSubject.next(user);

            const courses = [
                { id: 1, title: 'Course 1' },
                { id: 2, title: 'Course 2' },
            ] as Course[];

            coursesSubject.next(courses);

            expect(websocketServiceMock.subscribe).toHaveBeenCalledTimes(2);
            expect(websocketServiceMock.subscribe).toHaveBeenCalledWith('/user/topic/communication/notification/1');
            expect(websocketServiceMock.subscribe).toHaveBeenCalledWith('/user/topic/communication/notification/2');
        });

        it('should not resubscribe to same course twice', () => {
            const user = { id: 'user1' } as unknown as User;
            userSubject.next(user);
            vi.clearAllMocks();
            websocketServiceMock.subscribe.mockReturnValue(websocketReceiveSubject.asObservable());

            const courses = [{ id: 1, title: 'Course 1' }] as Course[];

            coursesSubject.next(courses);
            coursesSubject.next(courses);

            expect(websocketServiceMock.subscribe).toHaveBeenCalledOnce();
        });

        it('should handle incoming notifications and pass them to the notification service', () => {
            const user = { id: 'user1' } as unknown as User;
            userSubject.next(user);

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

            expect(courseNotificationServiceMock.addNotification).toHaveBeenCalledOnce();

            expect(courseNotificationServiceMock.addNotification.mock.calls[0][0]).toBe(1);

            const processedNotification = courseNotificationServiceMock.addNotification.mock.calls[0][1];
            expect(processedNotification.notificationId).toBe(123);
            expect(processedNotification.courseId).toBe(1);
            expect(processedNotification.notificationType).toBe('newPostNotification');
            expect(processedNotification.creationDate).toBeDefined();
            expect(processedNotification.parameters).toEqual({ key: 'value' });
        });

        it('should emit received notifications via websocketNotification$ observable', async () => {
            const user = { id: 'user1' } as unknown as User;
            userSubject.next(user);

            const notificationPromise = firstValueFrom(service.websocketNotification$);
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

            const receivedNotification = await notificationPromise;
            expect(receivedNotification.notificationId).toBe(123);
            expect(receivedNotification.courseId).toBe(1);
        });

        it('should do nothing when courses list is undefined', () => {
            const user = { id: 'user1' } as unknown as User;
            userSubject.next(user);

            coursesSubject.next(undefined);
            expect(websocketServiceMock.subscribe).not.toHaveBeenCalled();
        });

        it('should handle courses without IDs properly', () => {
            const user = { id: 'user1' } as unknown as User;
            userSubject.next(user);

            const courses = [{ title: 'Course without ID' }, { id: 2, title: 'Course 2' }] as Course[];

            coursesSubject.next(courses);

            expect(websocketServiceMock.subscribe).toHaveBeenCalledOnce();
            expect(websocketServiceMock.subscribe).toHaveBeenCalledWith('/user/topic/communication/notification/2');
        });
    });

    describe('User subscription handling', () => {
        it('should not subscribe to courses when user is not authenticated', () => {
            userSubject.next(null);

            expect(courseManagementServiceMock.getCoursesForNotifications).not.toHaveBeenCalled();
            expect(websocketServiceMock.subscribe).not.toHaveBeenCalled();
        });

        it('should cleanup subscriptions and resubscribe when user changes', () => {
            const user1 = { id: 'user1' } as unknown as User;
            userSubject.next(user1);

            const courses1 = [{ id: 1, title: 'Course 1' }] as Course[];
            coursesSubject.next(courses1);

            expect(websocketServiceMock.subscribe).toHaveBeenCalledWith('/user/topic/communication/notification/1');

            vi.clearAllMocks();
            websocketServiceMock.subscribe.mockReturnValue(websocketReceiveSubject.asObservable());

            const user2 = { id: 'user2' } as unknown as User;
            userSubject.next(user2);

            expect(courseManagementServiceMock.getCoursesForNotifications).toHaveBeenCalledOnce();

            const courses2 = [{ id: 2, title: 'Course 2' }] as Course[];
            coursesSubject.next(courses2);

            expect(websocketServiceMock.subscribe).toHaveBeenCalledWith('/user/topic/communication/notification/2');
        });

        it('should not resubscribe when the same user is emitted twice', () => {
            const user = { id: 'user1' } as unknown as User;
            userSubject.next(user);

            const courses = [{ id: 1, title: 'Course 1' }] as Course[];
            coursesSubject.next(courses);

            expect(courseManagementServiceMock.getCoursesForNotifications).toHaveBeenCalledOnce();
            expect(websocketServiceMock.subscribe).toHaveBeenCalledOnce();

            vi.clearAllMocks();
            websocketServiceMock.subscribe.mockReturnValue(websocketReceiveSubject.asObservable());

            userSubject.next(user);

            expect(courseManagementServiceMock.getCoursesForNotifications).not.toHaveBeenCalled();
            expect(websocketServiceMock.subscribe).not.toHaveBeenCalled();
        });
    });

    describe('Cleanup', () => {
        it('should properly clean up subscriptions on ngOnDestroy', () => {
            const cleanupSpy = vi.spyOn(service as any, 'cleanupSubscriptions');

            const user = { id: 'user1' } as unknown as User;
            userSubject.next(user);

            const courses = [{ id: 1, title: 'Course 1' }] as Course[];
            coursesSubject.next(courses);

            service.ngOnDestroy();

            expect(cleanupSpy).toHaveBeenCalled();
        });

        it('should unsubscribe from existing course subscription on cleanup', () => {
            const user = { id: 'user1' } as unknown as User;
            userSubject.next(user);

            const unsubscribeSpy = vi.fn();

            (service as any).coursesSubscription = { unsubscribe: unsubscribeSpy };

            (service as any).cleanupSubscriptions();

            expect(unsubscribeSpy).toHaveBeenCalled();
            expect((service as any).coursesSubscription).toBeUndefined();
        });
    });
});
